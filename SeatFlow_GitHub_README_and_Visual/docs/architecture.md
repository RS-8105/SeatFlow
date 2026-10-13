# SeatFlow — System Architecture & Concurrency Design

This document details the complete production architecture, concurrency controls, real-time streaming pipeline, and data guarantees implemented in the **SeatFlow** high-concurrency ticket booking engine.

---

## 1. Executive Architecture Overview

SeatFlow is engineered to solve the classic high-concurrency event ticketing problem: **guaranteeing zero double-bookings during flash-sale traffic spikes while maintaining sub-second latency and real-time state synchronization across thousands of concurrent clients.**

The platform employs a **defense-in-depth architecture**:
* **Optimized In-Memory Locking (Fast Path):** Upstash Redis with atomic Lua scripts coordinates multi-seat temporary holds (300-second TTL), enforcing mutual exclusion and user quota limits before touching persistent storage.
* **Transactional ACID Persistence (Durable Path):** Neon PostgreSQL provides durable ledger storage with database-level partial unique constraints and pessimistic row locking (`SELECT FOR UPDATE`) as the final defense against race conditions.
* **Real-Time Reactive Streaming:** Redis Pub/Sub acts as an inter-node message bus, fanning out seat status transitions to Spring Boot Server-Sent Events (SSE) emitters for instantaneous client UI synchronization.

---

## 2. End-to-End System Architecture Diagram

```mermaid
flowchart TB
    %% =========================================================================
    %% SEATFLOW SYSTEM ARCHITECTURE
    %% =========================================================================

    subgraph CLIENT_LAYER["1. Frontend & Client Layer (Vercel)"]
        direction TB
        Browser["Patron Web Browser"]
        
        subgraph VERCEL_APP["React 18 Single Page Application"]
            Router["React Router v6<br/>(Protected & Public Routes)"]
            TanStack["TanStack React Query v5<br/>(Client Cache & State)"]
            ApiClient["API Client (Fetch)<br/>(Bearer Token & Idempotency)"]
            EventSourceClient["Native EventSource Client<br/>(SSE Listener)"]
            
            Router --> TanStack
            TanStack --> ApiClient
            TanStack -.-> EventSourceClient
        end
        Browser <--> VERCEL_APP
    end

    subgraph CLOUD_EDGE["Cloud Edge & Security Routing"]
        VercelCDN["Vercel Global CDN Edge<br/>(HTTPS / SSL Termination)"]
        RenderRouter["Render Ingress Router<br/>(HTTPS / TLS / Reverse Proxy)"]
    end

    subgraph BACKEND_LAYER["2. Backend API Layer (Render - Spring Boot 3.3.4 / Java 21)"]
        direction TB
        
        subgraph SECURITY_PIPELINE["Spring Security Filter Chain"]
            CorsFilter["CORS Configuration<br/>(Allowed Origins & SSE Headers)"]
            JwtFilter["JwtAuthenticationFilter<br/>(Stateless Bearer Validation)"]
            SecurityConfig["SecurityFilterChain<br/>(Role & Endpoint Access)"]
            
            CorsFilter --> JwtFilter --> SecurityConfig
        end

        subgraph REST_CONTROLLERS["REST Controllers"]
            AuthController["AuthController<br/>(/api/auth/**)"]
            EventController["EventController<br/>(/api/events/**)"]
            ShowtimeController["ShowtimeController<br/>(/api/showtimes/**)"]
            HoldController["HoldController<br/>(/api/holds)"]
            BookingController["BookingController<br/>(/api/holds/confirm, /api/bookings/**)"]
            DemoController["ConcurrencyLabController<br/>(/api/demo/**)"]
        end

        subgraph APPLICATION_SERVICES["Core Domain Services"]
            AuthService["AuthService & JwtService<br/>(BCrypt / Token Issuance)"]
            HoldService["HoldService<br/>(Quotas & Hold Lifecycle)"]
            BookingService["BookingService<br/>(Idempotency & Orchestration)"]
            PaymentService["PaymentService<br/>(Simulated Payment Lifecycle)"]
            ReconScheduler["HoldExpiryReconciliationScheduler<br/>(@Scheduled 30s)"]
            HeartbeatScheduler["SSE Heartbeat Scheduler<br/>(@Scheduled 15s)"]
        end

        subgraph TRANSACTIONAL_WRITERS["Transactional Isolation Boundaries"]
            TxBookingWriter["TransactionalBookingWriter<br/>(@Transactional + AfterCommit Sync)"]
            TxPaymentWriter["TransactionalPaymentWriter<br/>(@Transactional + SELECT FOR UPDATE)"]
        end

        SECURITY_PIPELINE --> REST_CONTROLLERS
        REST_CONTROLLERS --> APPLICATION_SERVICES
        APPLICATION_SERVICES --> TRANSACTIONAL_WRITERS
    end

    subgraph REALTIME_CONCURRENCY["3. Real-Time & Concurrency Layer (Upstash Redis)"]
        direction TB
        
        RedisTemplate["StringRedisTemplate & Lettuce Driver<br/>(TLS Port 6379)"]
        
        subgraph LUA_SCRIPTS["Atomic Redis Lua Scripts"]
            LuaMultiHold["ATOMIC_MULTI_ACQUIRE_SCRIPT<br/>(All-or-nothing hold + TTL)"]
            LuaCompareDel["COMPARE_AND_DELETE_SCRIPT<br/>(Token-verified release)"]
            LuaReleaseOwner["RELEASE_HOLD_WITH_OWNER_SCRIPT<br/>(Atomic delete & publish)"]
            LuaProbeAvail["PUBLISH_AVAILABLE_IF_UNHELD_SCRIPT<br/>(Atomic probe & notify)"]
        end

        subgraph REDIS_DATA["In-Memory State Store"]
            HoldKeys[("seat_hold:{showtime}:{seat}<br/>(Value: holdToken, TTL: 300s)")]
            OwnerKeys[("hold_owner:{holdToken}<br/>(Value: userId, TTL: 300s)")]
        end

        subgraph PUB_SUB_PIPELINE["Pub/Sub & SSE Fanout"]
            RedisTopic{{"Redis Topic<br/>'showtime:seat-events'"}}
            ListenerContainer["RedisMessageListenerContainer<br/>(Pub/Sub MessageListener)"]
            SseService["SseBroadcastService<br/>(ConcurrentHashMap Emitters)"]
        end

        RedisTemplate --> LUA_SCRIPTS
        LUA_SCRIPTS --> REDIS_DATA
        LUA_SCRIPTS -.->|PUBLISH| RedisTopic
        RedisTopic --> ListenerContainer
        ListenerContainer --> SseService
    end

    subgraph PERSISTENT_STORAGE["4. Persistent Relational Storage (Neon PostgreSQL)"]
        direction TB
        HikariPool["HikariCP Connection Pool<br/>(Max 30, Timeout 20s)"]
        
        subgraph NEON_TABLES["PostgreSQL Schema (Flyway V1 - V4)"]
            UsersTable[("users<br/>(Credentials & Passwords)")]
            CatalogTables[("events / venues / showtimes / seats<br/>(Catalogue Hierarchy)")]
            BookingsTable[("bookings<br/>(idempotency_key UNIQUE)")]
            BookingSeatsTable[("booking_seats<br/>(idx_unique_confirmed_seat_showtime)")]
            PaymentsTable[("payments<br/>(idx_unique_successful_payment_booking)")]
        end

        HikariPool --> NEON_TABLES
    end

    %% =========================================================================
    %% INTER-COMPONENT CONNECTIONS & PROTOCOLS
    %% =========================================================================
    
    VERCEL_APP -->|1. Static Assets / HTTPS| VercelCDN
    ApiClient -->|2. REST API / HTTPS| RenderRouter
    EventSourceClient -->|3. Streaming SSE / HTTPS| RenderRouter
    
    RenderRouter -->|4. HTTP Forwarding / Port 8080| SECURITY_PIPELINE
    SseService -->|5. text/event-stream chunks| RenderRouter
    
    HoldService <-->|6. Atomic Holds & Quota| RedisTemplate
    TxBookingWriter -->|7. After-Commit Lock Release| HoldService
    TxBookingWriter -->|8. Broadcast BOOKED Event| SseService
    
    APPLICATION_SERVICES -->|9. SQL Queries / HikariCP| HikariPool
    TRANSACTIONAL_WRITERS -->|10. ACID Writes & Row Locks| HikariPool

    %% Styling & Classes
    classDef client fill:#f8fafc,stroke:#334155,stroke-width:1.5px,color:#0f172a;
    classDef edge fill:#f0fdf4,stroke:#16a34a,stroke-width:1.5px,color:#14532d;
    classDef backend fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#1e3a8a;
    classDef concurrency fill:#fef2f2,stroke:#dc2626,stroke-width:1.5px,color:#7f1d1d;
    classDef storage fill:#faf5ff,stroke:#7c3aed,stroke-width:1.5px,color:#4c1d95;

    class Browser,VERCEL_APP,Router,TanStack,ApiClient,EventSourceClient client;
    class VercelCDN,RenderRouter edge;
    class SECURITY_PIPELINE,REST_CONTROLLERS,APPLICATION_SERVICES,TRANSACTIONAL_WRITERS,CorsFilter,JwtFilter,SecurityConfig,AuthController,EventController,ShowtimeController,HoldController,BookingController,DemoController,AuthService,HoldService,BookingService,PaymentService,ReconScheduler,HeartbeatScheduler,TxBookingWriter,TxPaymentWriter backend;
    class REALTIME_CONCURRENCY,RedisTemplate,LUA_SCRIPTS,REDIS_DATA,PUB_SUB_PIPELINE,LuaMultiHold,LuaCompareDel,LuaReleaseOwner,LuaProbeAvail,HoldKeys,OwnerKeys,RedisTopic,ListenerContainer,SseService concurrency;
    class PERSISTENT_STORAGE,HikariPool,NEON_TABLES,UsersTable,CatalogTables,BookingsTable,BookingSeatsTable,PaymentsTable storage;
```

---

## 3. End-to-End Booking Lifecycle & Concurrency Sequence

The sequence diagram below models the exact chronological lifecycle from discovery to simulated payment and cancellation, detailing:
1. Discovery and SSE connection establishment.
2. Atomic multi-seat hold acquisition via Redis Lua script.
3. Idempotent booking persistence with PostgreSQL transactional synchronization.
4. Pessimistic row-locking payment simulation.
5. Cancellation and automatic seat restoration.

```mermaid
sequenceDiagram
    autonumber
    actor Patron as Patron Browser (React)
    participant Edge as Render Router
    participant API as Spring Boot API
    participant Redis as Upstash Redis (Lua/Cache)
    participant PubSub as Redis Pub/Sub
    participant DB as Neon PostgreSQL (HikariCP)
    actor OtherPatrons as Other Connected Clients

    %% -------------------------------------------------------------------------
    %% 1. DISCOVERY & REAL-TIME SUBSCRIPTION
    %% -------------------------------------------------------------------------
    rect rgb(240, 249, 255)
        Note over Patron, OtherPatrons: Phase 1: Catalogue Discovery & Real-Time SSE Subscription
        Patron->>Edge: GET /api/showtimes/{id}/seats (HTTPS)
        Edge->>API: Route to ShowtimeController
        API->>DB: Query catalog & confirmed booking_seats
        API->>Redis: Probe active seat_hold:* keys
        API-->>Patron: 200 OK (Seats with AVAILABLE / HELD / BOOKED status)

        Patron->>Edge: GET /api/showtimes/{id}/stream (EventSource SSE)
        Edge->>API: ShowtimeController.streamSeatUpdates()
        API->>API: SseBroadcastService registers SseEmitter for showtimeId
        API-->>Patron: event: connected (200 OK text/event-stream)
    end

    %% -------------------------------------------------------------------------
    %% 2. ATOMIC MULTI-SEAT HOLD ACQUISITION
    %% -------------------------------------------------------------------------
    rect rgb(254, 242, 242)
        Note over Patron, Redis: Phase 2: Atomic Multi-Seat Temporary Hold (Lua Script)
        Patron->>Edge: POST /api/showtimes/{id}/holds {seatIds: [10, 11]} (Auth JWT)
        Edge->>API: ShowtimeController.createHold()
        API->>API: HoldService validates quota (seatCount <= 6, no duplicates)
        API->>DB: Verify seats exist and belong to showtime venue
        API->>DB: Verify seats are not already CONFIRMED in booking_seats
        API->>Redis: Execute ATOMIC_MULTI_ACQUIRE_SCRIPT (KEYS: seat_hold:{id}:{seat}, ARGV: token, 300s)
        
        alt Seat Already Held by Competitor
            Redis-->>API: Returns 0 (Conflict detected)
            API-->>Patron: 409 Conflict ("SeatAlreadyHeldException")
        else All Seats Free
            Redis-->>API: Returns 1 (Atomic all-or-nothing hold succeeded)
            API->>Redis: SET hold_owner:{token} = userId (TTL: 300s)
            API->>PubSub: PUBLISH 'showtime:seat-events' {seatId, status: 'HELD', expiresAt}
            PubSub-->>API: MessageListenerContainer receives event
            API->>Patron: SSE event: seat-update (status: HELD)
            API->>OtherPatrons: SSE event: seat-update (status: HELD)
            API-->>Patron: 201 Created {holdToken: UUID, expiresAt: T+300s}
        end
    end

    %% -------------------------------------------------------------------------
    %% 3. IDEMPOTENT BOOKING CONFIRMATION
    %% -------------------------------------------------------------------------
    rect rgb(240, 253, 244)
        Note over Patron, DB: Phase 3: Idempotent Booking Confirmation & DB Transaction
        Patron->>Edge: POST /api/holds/confirm (Header: Idempotency-Key, Body: {holdToken, seatIds})
        Edge->>API: BookingController.confirmHold()
        API->>DB: Check bookings by idempotency_key
        opt Idempotency Key Exists
            API-->>Patron: 200 OK (Return existing booking without re-executing)
        end
        API->>Redis: Validate hold ownership (hold_owner:{token} == userId && seat_hold:* == token)
        API->>DB: Begin @Transactional block
        API->>DB: Insert into bookings (status: CONFIRMED, idempotency_key, total_amount)
        API->>DB: Insert into booking_seats (status: CONFIRMED)
        Note over DB: Guarded by partial unique index idx_unique_confirmed_seat_showtime
        DB-->>API: Transaction Committed Successfully
        
        Note over API: TransactionSynchronization.afterCommit() Triggered
        API->>Redis: holdService.releaseHold() (Deletes seat_hold and hold_owner keys)
        API->>PubSub: PUBLISH 'showtime:seat-events' {seatId, status: 'BOOKED'}
        PubSub-->>API: MessageListenerContainer receives event
        API->>Patron: SSE event: seat-update (status: BOOKED)
        API->>OtherPatrons: SSE event: seat-update (status: BOOKED)
        API-->>Patron: 201 Created (BookingResponse with booking ID & total amount)
    end

    %% -------------------------------------------------------------------------
    %% 4. DETERMINISTIC PAYMENT SIMULATION
    %% -------------------------------------------------------------------------
    rect rgb(250, 245, 255)
        Note over Patron, DB: Phase 4: Deterministic Payment Simulation (Simulated, Not a Gateway)
        Patron->>Edge: POST /api/bookings/{id}/payment {simulateSuccess: true} (Auth JWT)
        Edge->>API: BookingController.payForBooking()
        API->>DB: Begin @Transactional block
        API->>DB: SELECT * FROM bookings WHERE id = ? FOR UPDATE (Pessimistic Row Lock)
        API->>DB: Check existing successful payments for booking (Idempotency check)
        opt Successful Payment Already Present
            API-->>Patron: 200 OK (Return existing Payment without duplicate charge)
        end
        API->>DB: Insert into payments (booking_id, status: SUCCESS, simulated: true)
        Note over DB: Guarded by partial unique index idx_unique_successful_payment_booking
        DB-->>API: Transaction Committed
        API-->>Patron: 201 Created (PaymentResponse: SUCCESS)
    end

    %% -------------------------------------------------------------------------
    %% 5. BOOKING CANCELLATION & SEAT RESTORATION
    %% -------------------------------------------------------------------------
    rect rgb(255, 251, 235)
        Note over Patron, OtherPatrons: Phase 5: Booking Cancellation & Dynamic Seat Restoration
        Patron->>Edge: POST /api/bookings/{id}/cancel (Auth JWT)
        Edge->>API: BookingController.cancelBooking()
        API->>DB: Begin @Transactional block
        API->>DB: SELECT * FROM bookings WHERE id = ? FOR UPDATE
        API->>DB: Update bookings SET status = 'CANCELLED'
        API->>DB: Update booking_seats SET status = 'CANCELLED'
        DB-->>API: Transaction Committed
        
        Note over API: TransactionSynchronization.afterCommit() Triggered
        API->>PubSub: PUBLISH 'showtime:seat-events' {seatId, status: 'AVAILABLE'}
        PubSub-->>API: MessageListenerContainer receives event
        API->>Patron: SSE event: seat-update (status: AVAILABLE)
        API->>OtherPatrons: SSE event: seat-update (status: AVAILABLE)
        API-->>Patron: 200 OK (Cancelled BookingResponse)
    end
```

---

## 4. Deep-Dive Component Technical Specifications

### A. Concurrency & Double-Booking Protection (Two-Tier Defense)

SeatFlow utilizes a dual-tier protection model to eliminate race conditions:

| Tier | Component | Mechanism | Guarantees |
| :--- | :--- | :--- | :--- |
| **Tier 1 (In-Memory)** | Upstash Redis | Atomic Lua Script (`ATOMIC_MULTI_ACQUIRE_SCRIPT`) | • Sub-millisecond mutual exclusion<br/>• Atomically checks all requested seats; if any seat is held, rejects entire request with zero side effects<br/>• Auto-expires stale holds after 300s via native Redis TTL<br/>• Enforces max 6 seats per user quota |
| **Tier 2 (Durable DB)** | Neon PostgreSQL | Partial Unique Index (`idx_unique_confirmed_seat_showtime`) | • Hard relational boundary: `CREATE UNIQUE INDEX ... WHERE status = 'CONFIRMED'`<br/>• Guarantees that even in the event of an unexpected Redis cache eviction or split-brain, PostgreSQL physically rejects concurrent duplicate seat confirmations |

### B. Idempotency Architecture
* **Hold Tokens:** Each hold is assigned an unguessable UUID token (`holdToken`). All subsequent release and confirmation requests must prove ownership of both the hold token and the authenticated user identity (`hold_owner:{holdToken}`).
* **Client Idempotency Key:** `POST /api/holds/confirm` mandates an `Idempotency-Key` HTTP header. The database enforces a `UNIQUE` constraint on `bookings.idempotency_key`. Redundant requests return the existing booking record rather than re-executing transactions.
* **Payment Idempotency:** Payment execution checks existing successful records under a pessimistic row lock (`SELECT FOR UPDATE`), backed by partial unique index `idx_unique_successful_payment_booking WHERE status = 'SUCCESS'`.

### C. Real-Time Streaming & Pub/Sub Architecture
* **Cluster-Safe Notification:** When a seat status changes (`HELD`, `AVAILABLE`, `BOOKED`), Spring Boot publishes an event to Redis channel `showtime:seat-events`.
* **Distributed Fanout:** All Spring Boot application nodes subscribe via `RedisMessageListenerContainer`. Upon receipt, each node pushes the update across active `SseEmitter` instances for that specific showtime.
* **Connection Liveness:** `SseBroadcastService.sendHeartbeats()` issues periodic `:ping` comments every 15 seconds to prevent browser timeouts and proxies (e.g., Cloudflare/Render) from terminating idle HTTP streams.
* **Reconciliation Engine:** `HoldExpiryReconciliationScheduler` runs on a 30-second fixed rate, auditing active showtime seats against Redis keys and publishing `"AVAILABLE"` events if holds have expired naturally without explicit release.

### D. Security & Identity
* **Authentication:** Stateless JSON Web Tokens (JJWT 0.12.6) signed using HMAC-SHA256 (`ticketbooking.jwt.secret`).
* **Password Security:** Salted BCrypt password encoding via `BCryptPasswordEncoder`.
* **CORS Policy:** Strict origin whitelisting (`https://seat-flow-peach.vercel.app` + development origins), explicitly prohibiting wildcard origins when `allowCredentials=true` is enabled.

---

## 5. Implementation Verification & Boundaries

* **Simulated Payment Engine:** The payment workflow (`PaymentService`, `TransactionalPaymentWriter`, `/api/bookings/{id}/payment`) is a **deterministic state-machine simulation** built for testing concurrency, idempotent retries, and failure recovery. It does **not** connect to external payment gateways (e.g., Stripe, PayPal, Razorpay).
* **Cross-Cloud Deployment:**
  - Frontend SPA hosted on **Vercel** with CDN edge caching and client-side routing.
  - Backend hosted on **Render** as a containerized Docker image running Eclipse Temurin Java 21 on a non-root user.
  - Database hosted on **Neon Serverless PostgreSQL** via SSL JDBC.
  - Cache hosted on **Upstash Serverless Redis** via TLS port 6379 with custom connection and command timeout resilience.
