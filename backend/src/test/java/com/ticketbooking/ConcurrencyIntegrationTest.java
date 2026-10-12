package com.ticketbooking;

import com.ticketbooking.controller.ConcurrencyLabController;
import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.dto.RaceRequest;
import com.ticketbooking.dto.RaceResponse;
import com.ticketbooking.dto.ResetDemoRequest;
import com.ticketbooking.dto.ResetDemoResponse;
import com.ticketbooking.exception.HoldNotFoundOrExpiredException;
import com.ticketbooking.exception.HoldOwnershipException;
import com.ticketbooking.exception.SeatAlreadyBookedException;
import com.ticketbooking.exception.SeatAlreadyHeldException;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowtimeRepository;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.ConcurrencyLabService;
import com.ticketbooking.service.HoldResult;
import com.ticketbooking.service.HoldService;
import com.ticketbooking.service.RedisLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TicketBookingApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConcurrencyIntegrationTest {

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisLockService redisLockService;

    @Autowired
    private ConcurrencyLabService concurrencyLabService;

    @Autowired
    private ConcurrencyLabController concurrencyLabController;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long SHOWTIME_ID = 1L;

    @BeforeEach
    @AfterEach
    public void cleanupState() {
        // Clear all Redis keys in current isolated test DB (Redis DB 1)
        try {
            Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection()
                    .serverCommands()
                    .flushDb();
        } catch (Exception ignored) {
        }

        // Clean up test bookings and booking seats from isolated DB (ticketbooking_test)
        try {
            bookingSeatRepository.deleteAll();
            bookingRepository.deleteAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Same-seat contention: Multiple users attempt to hold the same seat concurrently (at most one succeeds)")
    public void test01_sameSeatContention_onlyOneHoldGranted() throws InterruptedException {
        final Long targetSeatId = 1L;
        final int userCount = 20;

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(userCount);

        ConcurrentLinkedQueue<HoldResult> successfulHolds = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failedAttempts = new ConcurrentLinkedQueue<>();

        for (int i = 1; i <= userCount; i++) {
            final String userId = "user-contender-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    HoldResult result = holdService.createHold(SHOWTIME_ID, List.of(targetSeatId), userId);
                    successfulHolds.add(result);
                } catch (Throwable t) {
                    failedAttempts.add(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Release all 20 threads simultaneously
        startGate.countDown();
        boolean finished = doneGate.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(successfulHolds).hasSize(1);
        assertThat(failedAttempts).hasSize(userCount - 1);

        for (Throwable t : failedAttempts) {
            assertThat(t).isInstanceOf(SeatAlreadyHeldException.class);
        }

        HoldResult winner = successfulHolds.peek();
        assertThat(winner).isNotNull();
        assertThat(winner.seatIds()).containsExactly(targetSeatId);

        // Verify Redis lock is held exclusively by the winner's token
        String activeToken = redisLockService.getHoldToken(SHOWTIME_ID, targetSeatId);
        assertThat(activeToken).isEqualTo(winner.holdToken());
    }

    @Test
    @Order(2)
    @DisplayName("2. Different-seat concurrency: Users competing for different seats succeed independently")
    public void test02_differentSeatConcurrency_allIndependentRequestsSucceed() throws InterruptedException {
        final List<Long> independentSeatIds = List.of(5L, 6L, 7L, 8L, 9L);
        final int threadCount = independentSeatIds.size();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);

        ConcurrentLinkedQueue<HoldResult> successfulHolds = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            final Long seatId = independentSeatIds.get(i);
            final String userId = "independent-user-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    HoldResult result = holdService.createHold(SHOWTIME_ID, List.of(seatId), userId);
                    successfulHolds.add(result);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(failures).isEmpty();
        assertThat(successfulHolds).hasSize(threadCount);

        for (Long seatId : independentSeatIds) {
            assertThat(redisLockService.isSeatHeld(SHOWTIME_ID, seatId)).isTrue();
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. Hold ownership: A user cannot release another user's hold using an incorrect or mismatched token")
    public void test03_holdOwnership_cannotReleaseWithIncorrectTokenOrUser() {
        final Long seatId = 12L;
        final String ownerUserId = "alice-owner";
        final String attackerUserId = "mallory-attacker";

        HoldResult aliceHold = holdService.createHold(SHOWTIME_ID, List.of(seatId), ownerUserId);
        assertThat(aliceHold).isNotNull();

        // 1. Attacker attempts to release with Alice's valid token but their own user ID -> HoldOwnershipException
        assertThatThrownBy(() ->
                holdService.releaseHold(SHOWTIME_ID, List.of(seatId), aliceHold.holdToken(), attackerUserId)
        ).isInstanceOf(HoldOwnershipException.class);

        // 2. Attacker attempts to release with an arbitrary bogus token -> HoldNotFoundOrExpiredException
        String bogusToken = UUID.randomUUID().toString();
        assertThatThrownBy(() ->
                holdService.releaseHold(SHOWTIME_ID, List.of(seatId), bogusToken, attackerUserId)
        ).isInstanceOf(HoldNotFoundOrExpiredException.class);

        // Verify seat remains held in Redis by Alice
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID, seatId)).isTrue();
        assertThat(redisLockService.validateHoldOwnership(SHOWTIME_ID, seatId, aliceHold.holdToken())).isTrue();

        // 3. Legitimate owner releases successfully
        var releaseResult = holdService.releaseHold(SHOWTIME_ID, List.of(seatId), aliceHold.holdToken(), ownerUserId);
        assertThat(releaseResult.fullyReleased()).isTrue();
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID, seatId)).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("4. Hold expiry: After a hold expires, another user can acquire the seat")
    public void test04_holdExpiry_expiredHoldAllowsNewAcquisition() throws InterruptedException {
        final Long seatId = 15L;
        final String firstUser = "user-expired";
        final String secondUser = "user-successor";
        final String shortToken = UUID.randomUUID().toString();

        // Acquire a short 1-second hold directly via RedisLockService
        boolean acquired = redisLockService.acquireHold(SHOWTIME_ID, seatId, shortToken, 1);
        assertThat(acquired).isTrue();
        redisTemplate.opsForValue().set("hold_owner:" + shortToken, firstUser, Duration.ofSeconds(1));

        // Immediately, seat is held and cannot be acquired by another user
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID, seatId)).isTrue();

        // Wait for TTL expiration (1.3 seconds)
        Thread.sleep(1300);

        // Seat hold key should have naturally expired in Redis
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID, seatId)).isFalse();

        // Successor user can now acquire the seat through the high-level HoldService
        HoldResult successorHold = holdService.createHold(SHOWTIME_ID, List.of(seatId), secondUser);
        assertThat(successorHold).isNotNull();
        assertThat(successorHold.userId()).isEqualTo(secondUser);
        assertThat(redisLockService.getHoldToken(SHOWTIME_ID, seatId)).isEqualTo(successorHold.holdToken());
    }

    @Test
    @Order(5)
    @DisplayName("5. Duplicate confirmation: Repeating confirmation with same idempotency key does not create duplicates")
    public void test05_duplicateConfirmation_idempotencyReplayPreventsDuplicates() {
        final Long seatId = 18L;
        final String userId = "user-idempotent";
        final String idempotencyKey = "idem-key-" + UUID.randomUUID();

        HoldResult hold = holdService.createHold(SHOWTIME_ID, List.of(seatId), userId);

        // First confirmation
        Booking booking1 = bookingService.confirmBooking(
                SHOWTIME_ID,
                List.of(seatId),
                hold.holdToken(),
                userId,
                idempotencyKey
        );
        assertThat(booking1).isNotNull();
        assertThat(booking1.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Replay with exact same idempotency key
        Booking booking2 = bookingService.confirmBooking(
                SHOWTIME_ID,
                List.of(seatId),
                hold.holdToken(),
                userId,
                idempotencyKey
        );
        assertThat(booking2).isNotNull();
        assertThat(booking2.getId()).isEqualTo(booking1.getId());
        assertThat(booking2.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // Verify PostgreSQL only contains exactly 1 booking record and 1 booking seat record
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        assertThat(bookings).hasSize(1);
        long confirmedCount = bookingSeatRepository.countByShowtimeIdAndSeatIdAndStatus(SHOWTIME_ID, seatId, BookingStatus.CONFIRMED);
        assertThat(confirmedCount).isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("6. Concurrent booking attempts: Concurrent confirmations must not double-book the same seat")
    public void test06_concurrentBookingAttempts_cannotDoubleBookSeat() throws InterruptedException {
        final Long seatId = 22L;
        final String userA = "user-racer-a";
        final String userB = "user-racer-b";
        final String idempotencyKeyA = "race-key-a-" + UUID.randomUUID();
        final String idempotencyKeyB = "race-key-b-" + UUID.randomUUID();

        // Give both users valid simulated holds in Redis to test PostgreSQL race condition defense
        final String tokenA = UUID.randomUUID().toString();
        final String tokenB = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("hold_owner:" + tokenA, userA, Duration.ofSeconds(60));
        redisTemplate.opsForValue().set("hold_owner:" + tokenB, userB, Duration.ofSeconds(60));

        // Acquire hold for Token A in Redis
        redisLockService.acquireHold(SHOWTIME_ID, seatId, tokenA);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);

        ConcurrentLinkedQueue<Booking> successfulBookings = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        // Thread A uses Token A
        executor.submit(() -> {
            try {
                startGate.await();
                Booking b = bookingService.confirmBooking(SHOWTIME_ID, List.of(seatId), tokenA, userA, idempotencyKeyA);
                successfulBookings.add(b);
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneGate.countDown();
            }
        });

        // Thread B tries to confirm with Token B
        executor.submit(() -> {
            try {
                startGate.await();
                Booking b = bookingService.confirmBooking(SHOWTIME_ID, List.of(seatId), tokenB, userB, idempotencyKeyB);
                successfulBookings.add(b);
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneGate.countDown();
            }
        });

        startGate.countDown();
        boolean completed = doneGate.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(completed).isTrue();
        // Invariant: Exactly 1 booking succeeded, never 2
        assertThat(successfulBookings).hasSize(1);
        assertThat(errors).hasSize(1);

        // Database partial unique index strictly prevents multiple confirmed seats
        long confirmedInDb = bookingSeatRepository.countByShowtimeIdAndSeatIdAndStatus(SHOWTIME_ID, seatId, BookingStatus.CONFIRMED);
        assertThat(confirmedInDb).isEqualTo(1);
    }

    @Test
    @Order(7)
    @DisplayName("7. Persistence: A successful booking remains retrievable from the database after transaction completes")
    public void test07_persistence_successfulBookingRetrievableFromDatabase() {
        final Long seatId = 25L;
        final String userId = "user-persistent";
        final String idempotencyKey = "persist-key-" + UUID.randomUUID();

        HoldResult hold = holdService.createHold(SHOWTIME_ID, List.of(seatId), userId);
        Booking confirmed = bookingService.confirmBooking(SHOWTIME_ID, List.of(seatId), hold.holdToken(), userId, idempotencyKey);

        assertThat(confirmed.getId()).isNotNull();

        Booking retrieved = bookingRepository.findById(confirmed.getId()).orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getUserId()).isEqualTo(userId);
        assertThat(retrieved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(retrieved.getShowtime()).isNotNull();
        assertThat(retrieved.getShowtime().getEvent()).isNotNull();
        assertThat(retrieved.getShowtime().getVenue()).isNotNull();
        assertThat(retrieved.getBookingSeats()).hasSize(1);
        assertThat(retrieved.getBookingSeats().get(0).getSeat().getId()).isEqualTo(seatId);
        assertThat(retrieved.getBookingSeats().get(0).getSeat().getRowLabel()).isNotBlank();
        assertThat(retrieved.getBookingSeats().get(0).getSeat().getSeatNumber()).isNotNull();
    }

    @Test
    @Order(8)
    @DisplayName("8. Failure handling: Failed booking operations must not leave inconsistent records or corrupt states")
    public void test08_failureHandling_failedOperationsDoNotLeaveCorruptRecords() {
        final Long seatId = 35L;
        final String userId = "user-fail";
        final String fakeToken = UUID.randomUUID().toString();
        final String idempotencyKey = "fail-key-" + UUID.randomUUID();

        // 1. Attempting to confirm without a valid hold throws HoldNotFoundOrExpiredException
        assertThatThrownBy(() ->
                bookingService.confirmBooking(SHOWTIME_ID, List.of(seatId), fakeToken, userId, idempotencyKey)
        ).isInstanceOf(HoldNotFoundOrExpiredException.class);

        // Verify zero booking rows or booking seats were written
        assertThat(bookingRepository.findByUserId(userId)).isEmpty();
        assertThat(bookingSeatRepository.findByShowtimeIdAndSeatIdInAndStatus(SHOWTIME_ID, List.of(seatId), BookingStatus.CONFIRMED)).isEmpty();

        // 2. Already confirmed seat throws SeatAlreadyBookedException on second attempt
        HoldResult validHold = holdService.createHold(SHOWTIME_ID, List.of(seatId), userId);
        bookingService.confirmBooking(SHOWTIME_ID, List.of(seatId), validHold.holdToken(), userId, idempotencyKey);

        final String anotherUser = "user-second-attempt";
        final String anotherToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("hold_owner:" + anotherToken, anotherUser, Duration.ofSeconds(60));
        redisLockService.acquireHold(SHOWTIME_ID, seatId, anotherToken);

        assertThatThrownBy(() ->
                bookingService.confirmBooking(SHOWTIME_ID, List.of(seatId), anotherToken, anotherUser, "diff-key-" + UUID.randomUUID())
        ).isInstanceOf(SeatAlreadyBookedException.class);

        // Confirm only 1 booking row exists for the seat in database
        assertThat(bookingSeatRepository.countByShowtimeIdAndSeatIdAndStatus(SHOWTIME_ID, seatId, BookingStatus.CONFIRMED)).isEqualTo(1);
    }

    @Test
    @Order(9)
    @DisplayName("9. Concurrency Lab accuracy: Race endpoint success, rejection, and overselling metrics reflect actual outcomes")
    public void test09_concurrencyLabAccuracy_raceEndpointMetricsReflectActualResults() {
        final Long testSeatId = 40L;
        final int users = 50;

        RaceResponse raceResponse = concurrencyLabController.simulateRace(
                new RaceRequest(SHOWTIME_ID, testSeatId, users)
        );

        assertThat(raceResponse).isNotNull();
        assertThat(raceResponse.showtimeId()).isEqualTo(SHOWTIME_ID);
        assertThat(raceResponse.seatId()).isEqualTo(testSeatId);
        assertThat(raceResponse.requestedUsers()).isEqualTo(users);
        assertThat(raceResponse.totalAttempts()).isEqualTo(users);
        assertThat(raceResponse.successfulHolds()).isEqualTo(1);
        assertThat(raceResponse.rejectedHolds()).isEqualTo(users - 1);
        assertThat(raceResponse.failedAttempts()).isEqualTo(users - 1);
        assertThat(raceResponse.errorCount()).isEqualTo(0);
        assertThat(raceResponse.oversellPercentage()).isEqualTo(0.0);
        assertThat(raceResponse.attempts()).hasSize(users);

        // Validate individual attempt results
        long acquiredCount = raceResponse.attempts().stream().filter(a -> a.acquired()).count();
        long rejectedCount = raceResponse.attempts().stream().filter(a -> !a.acquired()).count();
        assertThat(acquiredCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(users - 1);

        for (var attempt : raceResponse.attempts()) {
            assertThat(attempt.status()).isIn("ACQUIRED", "REJECTED");
            assertThat(attempt.latencyMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @Order(10)
    @DisplayName("10. Demo reset: Resetting demo data clears only intended demo state and does not corrupt unrelated records")
    public void test10_demoReset_clearsOnlyIntendedDemoStateAndPreservesUnrelatedData() {
        final Long demoShowtimeId = 1L;
        final Long otherShowtimeId = 2L;

        // A. Setup on Demo Showtime 1: Seat 50 has a temporary unconfirmed hold
        final Long tempHeldSeatId = 50L;
        final String tempUser = "user-temp-held";
        HoldResult tempHold = holdService.createHold(demoShowtimeId, List.of(tempHeldSeatId), tempUser);
        assertThat(tempHold).isNotNull();
        assertThat(redisLockService.isSeatHeld(demoShowtimeId, tempHeldSeatId)).isTrue();

        // B. Setup on Demo Showtime 1: Seat 51 has a confirmed booking in PostgreSQL
        final Long confirmedSeatId = 51L;
        final String confirmedUser = "user-confirmed-keeper";
        final String confirmedIdempKey = "keep-key-" + UUID.randomUUID();
        HoldResult keeperHold = holdService.createHold(demoShowtimeId, List.of(confirmedSeatId), confirmedUser);
        Booking confirmedBooking = bookingService.confirmBooking(demoShowtimeId, List.of(confirmedSeatId), keeperHold.holdToken(), confirmedUser, confirmedIdempKey);
        assertThat(confirmedBooking).isNotNull();

        // C. Setup on Demo Showtime 2 (unrelated showtime): Seat 52 has an active hold
        final Long otherSeatId = 52L;
        final String otherUser = "user-other-showtime";
        HoldResult otherHold = holdService.createHold(otherShowtimeId, List.of(otherSeatId), otherUser);
        assertThat(otherHold).isNotNull();
        assertThat(redisLockService.isSeatHeld(otherShowtimeId, otherSeatId)).isTrue();

        // D. Execute demo reset targeting ONLY Demo Showtime 1 via controller
        ResetDemoResponse resetResponse = concurrencyLabController.resetDemo(
                new ResetDemoRequest(demoShowtimeId)
        );

        // E. Verify reset outcomes:
        assertThat(resetResponse).isNotNull();
        assertThat(resetResponse.showtimeId()).isEqualTo(demoShowtimeId);
        assertThat(resetResponse.releasedSeatCount()).isGreaterThanOrEqualTo(1);

        // Intended demo temporary hold is cleared in Redis
        assertThat(redisLockService.isSeatHeld(demoShowtimeId, tempHeldSeatId)).isFalse();

        // Confirmed booking on Demo Showtime 1 is PRESERVED in PostgreSQL
        Booking retrievedBooking = bookingRepository.findById(confirmedBooking.getId()).orElse(null);
        assertThat(retrievedBooking).isNotNull();
        assertThat(retrievedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        long confirmedInDb = bookingSeatRepository.countByShowtimeIdAndSeatIdAndStatus(demoShowtimeId, confirmedSeatId, BookingStatus.CONFIRMED);
        assertThat(confirmedInDb).isEqualTo(1);

        // Unrelated Showtime 2 hold is UNTOUCHED and active
        assertThat(redisLockService.isSeatHeld(otherShowtimeId, otherSeatId)).isTrue();

        // F. Verify attempting to reset a non-demo showtime is strictly rejected
        assertThatThrownBy(() -> concurrencyLabController.resetDemo(new ResetDemoRequest(999L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reset is only permitted on designated demo showtimes");
    }

    @Test
    @Order(11)
    @DisplayName("11. Isolation: Contention on seat A does not block or impact unrelated seat B")
    public void test11_isolation_contentionOnOneSeatDoesNotBlockUnrelatedSeat() throws InterruptedException {
        final Long contendedSeat = 30L;
        final Long independentSeat = 31L;

        // User A holds contested seat
        HoldResult holdA = holdService.createHold(SHOWTIME_ID, List.of(contendedSeat), "user-a");
        assertThat(holdA).isNotNull();

        // 10 users attack the contended seat
        int attackerCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(attackerCount + 1);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(attackerCount + 1);

        AtomicInteger rejectedContentionCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<HoldResult> independentResults = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < attackerCount; i++) {
            final String attacker = "attacker-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    holdService.createHold(SHOWTIME_ID, List.of(contendedSeat), attacker);
                } catch (SeatAlreadyHeldException sahe) {
                    rejectedContentionCount.incrementAndGet();
                } catch (Throwable ignored) {
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Concurrently, an independent patron reserves seat B
        executor.submit(() -> {
            try {
                startGate.await();
                HoldResult res = holdService.createHold(SHOWTIME_ID, List.of(independentSeat), "user-independent-b");
                independentResults.add(res);
            } catch (Throwable ignored) {
            } finally {
                doneGate.countDown();
            }
        });

        startGate.countDown();
        boolean completed = doneGate.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(completed).isTrue();
        assertThat(rejectedContentionCount.get()).isEqualTo(attackerCount);
        assertThat(independentResults).hasSize(1);
        assertThat(independentResults.peek().seatIds()).containsExactly(independentSeat);
    }
}
