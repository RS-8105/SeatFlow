package com.ticketbooking;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.dto.RaceResponse;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        // Clear all Redis keys in current test DB
        try {
            Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection()
                    .serverCommands()
                    .flushDb();
        } catch (Exception ignored) {
        }

        // Clean up test bookings and booking seats from DB
        try {
            bookingSeatRepository.deleteAll();
            bookingRepository.deleteAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("1. Same-seat contention: Only 1 of multiple concurrent users acquires the hold")
    public void testSameSeatContention_onlyOneHoldGranted() throws InterruptedException {
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

        // Verify Redis lock is held by the winner's token
        String activeToken = redisLockService.getHoldToken(SHOWTIME_ID, targetSeatId);
        assertThat(activeToken).isEqualTo(winner.holdToken());
    }

    @Test
    @DisplayName("2. Different-seat concurrency: Independent concurrent requests for different seats all succeed")
    public void testDifferentSeatConcurrency_allIndependentRequestsSucceed() throws InterruptedException {
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
    @DisplayName("3. Hold ownership: Cannot release another user's hold with invalid or mismatched token")
    public void testHoldOwnership_cannotReleaseWithMismatchedCredentials() {
        final Long seatId = 12L;
        final String ownerUserId = "alice-owner";
        final String attackerUserId = "mallory-attacker";

        HoldResult aliceHold = holdService.createHold(SHOWTIME_ID, List.of(seatId), ownerUserId);
        assertThat(aliceHold).isNotNull();

        // 1. Attacker attempts to release with Alice's valid token but their own user ID -> HoldOwnershipException
        assertThatThrownBy(() ->
                holdService.releaseHold(SHOWTIME_ID, List.of(seatId), aliceHold.holdToken(), attackerUserId)
        ).isInstanceOf(HoldOwnershipException.class);

        // 2. Attacker attempts to release with a bogus token -> HoldNotFoundOrExpiredException
        String bogusToken = UUID.randomUUID().toString();
        assertThatThrownBy(() ->
                holdService.releaseHold(SHOWTIME_ID, List.of(seatId), bogusToken, attackerUserId)
        ).isInstanceOf(HoldNotFoundOrExpiredException.class);

        // Verify seat is STILL safely held by Alice
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID, seatId)).isTrue();
        assertThat(redisLockService.validateHoldOwnership(SHOWTIME_ID, seatId, aliceHold.holdToken())).isTrue();

        // 3. Legitimate owner successfully releases
        var releaseResult = holdService.releaseHold(SHOWTIME_ID, List.of(seatId), aliceHold.holdToken(), ownerUserId);
        assertThat(releaseResult.fullyReleased()).isTrue();
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID, seatId)).isFalse();
    }

    @Test
    @DisplayName("4. Hold expiration: An expired hold automatically allows new user acquisition")
    public void testHoldExpiration_expiredHoldAllowsNewAcquisition() throws InterruptedException {
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
    @DisplayName("5. Duplicate confirmation: Same idempotency key replay returns same booking without duplicate rows")
    public void testDuplicateConfirmation_idempotencyReplayIsIdempotent() {
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
    @DisplayName("6. Double booking protection: Concurrent booking confirmations for the same seat cannot double book")
    public void testDoubleBookingProtection_concurrentConfirmsCannotDoubleBook() throws InterruptedException {
        final Long seatId = 22L;
        final String userA = "user-racer-a";
        final String userB = "user-racer-b";
        final String idempotencyKeyA = "race-key-a-" + UUID.randomUUID();
        final String idempotencyKeyB = "race-key-b-" + UUID.randomUUID();

        // Give both users valid simulated holds in Redis to test DB race condition defense
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
        // Crucial invariant: Exactly 1 booking succeeded, never 2
        assertThat(successfulBookings).hasSize(1);
        assertThat(errors).hasSize(1);

        // Database partial unique index strictly prevents multiple confirmed seats
        long confirmedInDb = bookingSeatRepository.countByShowtimeIdAndSeatIdAndStatus(SHOWTIME_ID, seatId, BookingStatus.CONFIRMED);
        assertThat(confirmedInDb).isEqualTo(1);
    }

    @Test
    @DisplayName("7. Persistence: Confirmed booking is retrievable with showtime and seats eagerly loaded")
    public void testPersistence_confirmedBookingCanBeRetrievedAuthoritatively() {
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
    @DisplayName("8. Isolation: Contention on seat A does not block or impact unrelated seat B")
    public void testIsolation_contentionOnOneSeatDoesNotBlockUnrelatedSeat() throws InterruptedException {
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

    @Test
    @DisplayName("9. Failure handling: Failed confirmations leave zero orphan records or dirty states")
    public void testFailureHandling_invalidConfirmationLeavesCleanDatabaseState() {
        final Long seatId = 35L;
        final String userId = "user-fail";
        final String fakeToken = UUID.randomUUID().toString();
        final String idempotencyKey = "fail-key-" + UUID.randomUUID();

        // Attempting to confirm without a valid Redis hold throws HoldNotFoundOrExpiredException
        assertThatThrownBy(() ->
                bookingService.confirmBooking(SHOWTIME_ID, List.of(seatId), fakeToken, userId, idempotencyKey)
        ).isInstanceOf(HoldNotFoundOrExpiredException.class);

        // Verify zero booking rows or booking seats were written
        assertThat(bookingRepository.findByUserId(userId)).isEmpty();
        assertThat(bookingSeatRepository.findByShowtimeIdAndSeatIdInAndStatus(SHOWTIME_ID, List.of(seatId), BookingStatus.CONFIRMED)).isEmpty();
    }

    @Test
    @DisplayName("10. Concurrency Lab Simulation: 50 concurrent threads verify metrics and zero oversell")
    public void testConcurrencyLabRaceSimulation_verifiesMetricsAndZeroOversell() {
        final Long testSeatId = 40L;
        final int users = 50;

        RaceResponse raceResponse = concurrencyLabService.simulateRace(SHOWTIME_ID, testSeatId, users);

        assertThat(raceResponse).isNotNull();
        assertThat(raceResponse.requestedUsers()).isEqualTo(users);
        assertThat(raceResponse.totalAttempts()).isEqualTo(users);
        assertThat(raceResponse.successfulHolds()).isEqualTo(1);
        assertThat(raceResponse.rejectedHolds()).isEqualTo(users - 1);
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
}
