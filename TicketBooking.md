# Ticket Booking --- Engineering Project Bible

> **How to read this document**
>
> This project is already built (backend). This bible records what exists, what must be **audited**, and what to **add**. It was written from the resume description only, so every item marked **[AUDIT]** must be checked against your real code before it appears on your resume.
>
> -   **[EXISTING]** -- described on your resume as already implemented
> -   **[AUDIT]** -- verify in your code; fix if missing
> -   **[NEW]** -- to be built

## 0. Project identity

**Project:** Ticket Booking (High-Concurrency Seat Reservation System)\
**Category:** Backend systems / Concurrency / Full-stack\
**Primary user:** Event attendees (and organizers who manage events)\
**Core question:** "What happens when many people try to book the same seat at the same moment?"\
**Primary SDE signal:** Concurrency control, distributed locking, TTL-based expiry, transactional consistency, idempotency, real-time updates, failure handling, load testing.

### One-line product definition

A ticket booking platform that lets users hold, pay for, and confirm seats with a time-limited reservation workflow, guaranteeing that a seat is never sold twice even under heavy concurrent load.

### Important boundary

This is NOT a Ticketmaster clone and does not process real payments.

Its purpose is:

> **Demonstrating correct, observable behavior under concurrent seat contention.**

Payment is **simulated**. State this plainly in the README and on the resume.

------------------------------------------------------------------------

# 1. Problem

Two users click the same seat at the same moment. Exactly one must win.

Naive implementations fail in predictable ways:

``` text
check seat is free   (both see "free")
        ↓
reserve seat         (both succeed)
        ↓
two tickets sold for one seat
```

Other problems appear around the edges:

-   a user reserves a seat and never pays
-   payment arrives after the hold expired
-   a client retries a request and creates two bookings
-   the cache and the database disagree about who owns a seat

The product solves this with a hold-then-confirm workflow where **PostgreSQL is the source of truth** and **Redis manages temporary holds**.

------------------------------------------------------------------------

# 2. Users

## Primary

-   Attendees who browse events and book seats

## Secondary

-   Organizers / admins who create events, venues, and showtimes

## Initial target

A single-region deployment with one Redis instance and one PostgreSQL database. State this limitation honestly (see section 6).

------------------------------------------------------------------------

# 3. Booking workflow

``` text
Browse event
     ↓
Select seat(s)
     ↓
HOLD  (Redis SET NX EX, time-limited)
     ↓
Checkout (simulated payment)
     ↓
CONFIRM  (re-validate hold, write booking to PostgreSQL)
     ↓
Booking finalized, hold released
```

Alternative paths:

``` text
Hold expires          → seat becomes available again
User cancels hold     → seat released immediately
Payment fails         → hold released (or retained until TTL, by policy)
Booking cancelled     → seat released, refund simulated
```

------------------------------------------------------------------------

# 4. Features

## 4.1 Existing backend [EXISTING]

-   events, venues, showtimes
-   seat management
-   bookings and cancellations
-   payment workflow (simulated)
-   Redis distributed seat locking with atomic `SET NX EX`
-   hold-then-confirm workflow with TTL-based expiry
-   PostgreSQL as source of truth for confirmed bookings
-   concurrency tests: simultaneous requests for one seat, exactly one succeeds

## 4.2 Hardening checklist [AUDIT]

Verify each item in your code. These are the questions interviewers ask.

| Item | Why it matters |
|---|---|
| Lock value is a **unique token** (for example a UUID), not a constant | Lets you tell whose hold it is |
| Release uses an **atomic compare-and-delete** (Lua script), not a plain `DEL` | Prevents deleting another user's hold after your TTL expired |
| **Database unique constraint** on a confirmed seat per showtime | Last line of defense if Redis fails or a lock is wrong |
| Confirm step **re-validates** that the hold still belongs to this user | Handles "payment arrived after the hold expired" |
| Booking creation is **idempotent** (idempotency key) | Handles client retries and double-clicks |
| Multi-seat holds are **all-or-nothing** | Avoids partially held orders |
| Behavior when **Redis is unavailable** is defined | Fail closed (reject holds) rather than risk double-booking |
| A **reconciliation / cleanup job** exists | Handles crashes between payment and confirm |
| TTL is longer than the realistic checkout time, and shorter than a seat-hoarding window | Balances abandonment vs. unfairness |

Anything missing from this list becomes a task in the plan (section 17).

## 4.3 New features

### Seat-hoarding protection [NEW]

-   maximum seats held per user per showtime
-   maximum concurrent holds per user
-   rate limiting on hold requests

### Real-time seat updates [NEW]

-   Server-Sent Events (SSE) stream per showtime
-   seat state changes (held, released, booked) are pushed to all viewers
-   clients reconnect automatically and re-sync full state on reconnect

### Concurrency Lab (demo feature) [NEW]

A page and endpoint that makes the concurrency work visible:

-   user picks a demo showtime and a seat
-   clicks **"Simulate N users racing for this seat"**
-   backend runs N concurrent hold attempts server-side
-   UI shows: 1 winner, N-1 rejected, latency distribution, and a final check that exactly one booking exists

This is the single most effective demo in the project. It turns "I handled concurrency" into something a recruiter sees in ten seconds.

### Demo mode [NEW]

-   "Try demo" button, no registration required
-   seeded events, venues, and showtimes
-   demo showtimes reset on a schedule
-   payment is clearly labeled as simulated
-   hold, booking, and race endpoints are rate-limited
-   demo data is isolated from real accounts

### Observability [NEW]

-   OpenTelemetry Java agent instrumentation (traces for HTTP, JDBC, Redis)
-   structured logs including trace ID
-   counters: holds acquired, holds rejected, holds expired, bookings confirmed, confirm failures

This also makes the project the primary demo application for TraceLens.

------------------------------------------------------------------------

# 5. Non-goals

Do not build:

-   real payment processing
-   recommendation systems
-   dynamic pricing
-   a seat-map editor for arbitrary venue shapes (use a simple grid or rows)
-   multi-region deployment
-   mobile apps

These would dilute the concurrency story.

------------------------------------------------------------------------

# 6. Concurrency design

## Hold acquisition

``` text
SET seat_hold:{showtimeId}:{seatId} {holdToken} NX EX {ttlSeconds}
```

-   `NX` ensures only one holder
-   `EX` ensures the hold expires automatically
-   `holdToken` identifies the owner

For multiple seats, acquire all or roll back the ones already acquired (consider a Lua script to make this atomic).

## Safe release

Never release with a plain `DEL`. Use compare-and-delete:

``` text
if redis.call("GET", key) == token then
    return redis.call("DEL", key)
else
    return 0
end
```

Without this, a slow client can delete a hold that now belongs to someone else.

## Confirmation

``` text
1. Verify the hold token still matches (Redis).
2. Open a database transaction.
3. Insert the booking and seat rows.
   (A unique constraint rejects a second confirmed booking for the same seat/showtime.)
4. Commit.
5. Release the hold.
```

If step 1 passes but a delay occurs before step 3, the unique constraint still protects correctness.

## Idempotency

Clients send an idempotency key with the confirm request. The same key returns the original result and never creates a second booking.

## Known limitations (say these in interviews)

-   A single Redis instance is a single point of failure, and holds can be lost on failover. The database constraint is why this does not cause double-booking.
-   Distributed locks with TTLs are not perfectly safe if a process pauses longer than the TTL. Again, the database is the final authority.
-   This design is not multi-region.

Being able to explain these limits is worth more than claiming the system is "fully safe."

------------------------------------------------------------------------

# 7. Data model

Suggested schema. **Reconcile with your actual schema** and keep what you have.

## events

``` text
id
name
description
created_at
```

## venues

``` text
id
name
city
```

## showtimes

``` text
id
event_id
venue_id
starts_at
```

## seats

``` text
id
venue_id
row_label
seat_number
category
```

## bookings

``` text
id
user_id
showtime_id
status          (CONFIRMED, CANCELLED)
idempotency_key
total_amount
created_at
```

## booking_seats

``` text
booking_id
showtime_id
seat_id
status          (CONFIRMED, CANCELLED)
```

Constraint:

``` text
UNIQUE (showtime_id, seat_id) WHERE status = 'CONFIRMED'
```

(A partial unique index in PostgreSQL.)

## payments (simulated)

``` text
id
booking_id
status
simulated
created_at
```

## Redis keys

``` text
seat_hold:{showtimeId}:{seatId}  →  holdToken   (TTL)
user_holds:{userId}:{showtimeId} →  set of seats (TTL, optional)
```

------------------------------------------------------------------------

# 8. API design

Suggested. **Match your existing API** and add what is missing.

## Browsing

``` http
GET  /api/events
GET  /api/events/{id}
GET  /api/showtimes/{id}/seats
```

## Holds and booking

``` http
POST   /api/showtimes/{id}/holds          (create hold)
DELETE /api/holds/{holdId}                (release hold)
POST   /api/holds/{holdId}/confirm        (idempotency-key header)
POST   /api/bookings/{id}/cancel
GET    /api/bookings/me
```

## Real-time

``` http
GET /api/showtimes/{id}/stream            (SSE)
```

## Demo

``` http
POST /api/demo/race                       (showtimeId, seatId, users)
POST /api/demo/reset
```

Demo endpoints are rate-limited and only operate on demo showtimes.

------------------------------------------------------------------------

# 9. Real-time updates (SSE)

Flow:

``` text
Hold created / released / confirmed
          ↓
Publish event (Redis Pub/Sub or in-process)
          ↓
SSE endpoint streams to subscribers of that showtime
          ↓
React updates seat state
```

Design notes:

-   Store an explicit **expiry timestamp** with each hold and let the client compute the countdown from it. Do not rely on expiry notifications alone, because they are best-effort.
-   On reconnect, the client fetches the full seat state, then resumes the stream.
-   Use polling as a fallback if SSE is unavailable.
-   If you run more than one backend instance, broadcast through Redis Pub/Sub so every instance can notify its own subscribers.

------------------------------------------------------------------------

# 10. Frontend

## Stack

-   React
-   TypeScript
-   Vite
-   React Router
-   TanStack Query
-   Tailwind CSS
-   shadcn/ui (design system shared across the portfolio projects)
-   Recharts (Concurrency Lab latency chart)

## Pages

1.  Landing / Try demo
2.  Events list
3.  Event details and showtimes
4.  **Seat selection**
5.  Checkout (simulated payment)
6.  My bookings
7.  Login / Register
8.  **Concurrency Lab**
9.  Admin: create event, venue, showtime (minimal)

## Seat selection page (the centerpiece)

``` text
          SCREEN
  ---------------------------
  A  o o o o x x o o o o
  B  o o h o o o o x o o
  C  o o o o o o o o o o

  o available   h held   x booked   ● your selection

  Hold expires in 04:32        [ Continue to checkout ]
```

Requirements:

-   seat map that distinguishes available, held (by someone else), booked, and selected, **not by color alone** (use icons or patterns for accessibility)
-   hold countdown visible while seats are held
-   live updates through SSE
-   clear handling of conflicts: "Seat A4 was just taken" with a graceful rollback of the selection
-   when the hold expires, the UI releases the selection and explains why
-   loading skeletons, empty states, error states
-   dark mode
-   usable on mobile (scrollable seat map, adequately sized tap targets)

## Concurrency Lab page

-   showtime and seat selector (demo data only)
-   user-count input with a sensible cap
-   run button and a live result: winner, rejected count, errors
-   latency chart (Recharts)
-   final verification: "Confirmed bookings for this seat: 1"

## Frontend effort

Budget roughly 25-30% of total effort. The concurrency backend is the technical asset, but the seat map and the Concurrency Lab are what make a visitor stay.

------------------------------------------------------------------------

# 11. Failure cases

### Redis unavailable

Reject new holds with a clear error. Do not fall back to unprotected booking.

### Hold expires during payment

Confirm re-validates the hold. If it is gone and the seat is still free, either re-acquire it atomically or reject the request with a clear message. Decide and document the policy.

### Duplicate confirm request

The idempotency key returns the original result.

### Crash after payment, before booking is written

A reconciliation job finds payments without bookings (or holds with completed payments) and resolves them.

### Abandoned holds

TTL expiry releases the seat. A sweeper reconciles any holds that expired while a broadcast was missed.

### Cancellation

Cancelling marks the booking and seats as cancelled, releases seats for sale, and simulates a refund.

### Clock differences

Use server time only. The client displays a countdown from the server-provided expiry.

### Malformed or abusive requests

Validate input, enforce per-user hold limits, and rate-limit.

------------------------------------------------------------------------

# 12. Testing

## Unit tests

-   hold token generation and ownership checks
-   compare-and-delete release logic
-   seat-state transitions
-   hold limits per user

## Integration tests (use Testcontainers for PostgreSQL and Redis)

-   concurrent holds on one seat: one winner
-   confirm after expiry
-   duplicate confirm with the same idempotency key
-   multi-seat holds roll back on partial failure
-   database constraint rejects a second confirmed booking even when the Redis hold is bypassed
-   Redis unavailable behavior

## Concurrency tests

Run with a range of concurrency levels, for example 10, 50, 200 simultaneous requests for the same seat.

Verify and record:

``` text
requests sent
successful holds         (expected: 1)
rejected holds           (expected: N - 1)
confirmed bookings       (expected: 1)
double bookings          (expected: 0)
```

## Load tests (k6, Gatling, or similar)

Measure:

-   hold throughput
-   p50 / p95 / p99 latency for hold and confirm
-   behavior under mixed read/write traffic
-   error rate
-   memory and connection usage

Publish the results in `BENCHMARKS.md`. These are measurements, not promises.

------------------------------------------------------------------------

# 13. Security

-   JWT authentication (reuse what you already built)
-   authorization: users see and cancel only their own bookings
-   admin role for event management
-   input validation
-   rate limiting on holds and demo endpoints
-   demo endpoints restricted to demo data
-   no secrets in the repository
-   no secrets in logs

------------------------------------------------------------------------

# 14. Observability

-   OpenTelemetry Java agent
-   trace ID in structured logs
-   counters for holds, rejections, expiries, confirmations, and failures
-   `/actuator/health` for deployment checks

This is what lets TraceLens use the project as its demo application.

------------------------------------------------------------------------

# 15. Deployment

Frontend:

-   Vercel

Backend:

-   Render / Fly.io / Railway equivalent

Database:

-   Neon PostgreSQL

Redis:

-   a managed Redis service or a small self-hosted instance

Local:

-   `docker compose up` starts backend, PostgreSQL, and Redis

Notes:

-   Check the current free-tier limits of each service before committing, because they change.
-   Free-tier backends may sleep when idle, so the frontend needs a clear loading state for the first request.
-   The README must include a one-command local run and a GIF of the seat map and the Concurrency Lab.

------------------------------------------------------------------------

# 16. Interview questions

1.  Why Redis for holds and PostgreSQL for bookings?
2.  What does `SET NX EX` do, and why atomically?
3.  Why use a unique token as the lock value?
4.  What goes wrong if you release a lock with a plain `DEL`?
5.  What happens if the hold expires during payment?
6.  Why do you need a database constraint if Redis already prevents double holds?
7.  What happens if Redis goes down?
8.  How is idempotency implemented?
9.  How do you hold multiple seats safely?
10. How do you prevent one user from hoarding seats?
11. How do SSE updates work, and why not WebSockets?
12. How do you handle SSE reconnects and missed events?
13. What are the limits of a single-instance Redis lock?
14. How did you test concurrency, and what numbers did you get?
15. What would change at 100x traffic?
16. How would you shard or partition seat inventory?
17. How does cancellation interact with holds?
18. How do you reconcile after a crash between payment and booking?
19. What did the Concurrency Lab show you?
20. How would you trace a failed booking in production?

------------------------------------------------------------------------

# 17. Plan

The backend exists, so effort goes into auditing, hardening, and the frontend. Total roughly **4-6 weeks** of work, shorter if done alongside less demanding tasks; adjust to your schedule.

### Week 1

Audit against the hardening checklist (section 4.2). Fix gaps: token-based locks, compare-and-delete release, database constraint, idempotency, defined Redis-down behavior. Add or extend concurrency tests.

### Week 2

SSE stream, hold limits and rate limiting, demo endpoints (race and reset), seed data, OpenTelemetry instrumentation.

### Week 3

Frontend foundation: design system, routing, events list, event details, auth, seat selection with live updates and hold countdown.

### Week 4

Checkout, My bookings, Concurrency Lab page, conflict and expiry handling in the UI.

### Week 5

Load tests, `BENCHMARKS.md`, Testcontainers integration tests, security pass.

### Week 6

Deployment, demo mode polish, README with GIF, demo video, resume update.

## Minimum shippable version and cut order

Must ship (never cut):

-   hardened locking (token + safe release + database constraint)
-   idempotent confirm
-   seat selection page with hold countdown
-   Concurrency Lab
-   concurrency test results with real numbers
-   deployed demo with a no-login option

If the schedule slips, cut in this order:

1.  admin pages (seed data is enough)
2.  multi-seat holds (single-seat only)
3.  SSE (fall back to polling)
4.  full load-test suite (keep one documented concurrency run)

------------------------------------------------------------------------

# 18. Resume positioning

> Built a high-concurrency ticket booking platform with Redis-based distributed seat holds, time-limited hold-then-confirm workflow, and PostgreSQL as the source of truth, guaranteeing no double-booking under concurrent load.

> Implemented token-based atomic lock release, database uniqueness constraints as a final safeguard, idempotent booking confirmation, and concurrency tests ([N] parallel requests per seat, [X] double bookings).

> Built a React + TypeScript frontend with a live seat map (SSE), hold countdown, and a Concurrency Lab that visualizes concurrent booking races; deployed with a no-login demo.

Fill in real measured numbers. Include only features that exist in your code, and describe payment as simulated.
