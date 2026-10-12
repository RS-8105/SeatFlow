package com.ticketbooking.service;

import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.dto.RaceAttemptResult;
import com.ticketbooking.dto.RaceResponse;
import com.ticketbooking.dto.ResetDemoResponse;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowtimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ConcurrencyLabService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLabService.class);
    private static final Set<Long> DEMO_SHOWTIME_IDS = Set.of(1L, 2L);

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final RedisLockService redisLockService;
    private final SseBroadcastService sseBroadcastService;
    private final StringRedisTemplate redisTemplate;
    private final int maxRaceUsers;

    private final Semaphore simulationRateLimiter = new Semaphore(1);

    public ConcurrencyLabService(
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            BookingSeatRepository bookingSeatRepository,
            RedisLockService redisLockService,
            SseBroadcastService sseBroadcastService,
            StringRedisTemplate redisTemplate,
            @Value("${ticketbooking.demo.max-race-users:200}") int maxRaceUsers
    ) {
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.redisLockService = redisLockService;
        this.sseBroadcastService = sseBroadcastService;
        this.redisTemplate = redisTemplate;
        this.maxRaceUsers = maxRaceUsers;
    }

    public RaceResponse simulateRace(Long showtimeId, Long seatId, int userCount) {
        if (showtimeId == null) {
            throw new IllegalArgumentException("Showtime ID must not be null");
        }
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID must not be null");
        }
        if (userCount < 2 || userCount > maxRaceUsers) {
            throw new IllegalArgumentException("User count must be between 2 and " + maxRaceUsers);
        }

        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + showtimeId));

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        if (!seat.getVenue().getId().equals(showtime.getVenue().getId())) {
            throw new IllegalArgumentException("Seat " + seatId + " does not belong to showtime venue " + showtime.getVenue().getId());
        }

        if (!simulationRateLimiter.tryAcquire()) {
            throw new IllegalStateException("Another concurrency simulation is currently running. Please wait a moment.");
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(userCount, 32));
        Map<String, RaceAttemptResult> resultMap = new ConcurrentHashMap<>(userCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(userCount);

        try {
            for (int i = 1; i <= userCount; i++) {
                final String userId = "race-user-" + i;
                final String token = UUID.randomUUID().toString();

                executor.submit(() -> {
                    try {
                        boolean started = startGate.await(10, TimeUnit.SECONDS);
                        if (!started) {
                            resultMap.put(userId, new RaceAttemptResult(userId, false, 0, "Timed out waiting at start gate"));
                            return;
                        }

                        long start = System.nanoTime();
                        boolean acquired = redisLockService.acquireHold(showtimeId, seatId, token);
                        long latencyMs = Math.max(0, (System.nanoTime() - start) / 1_000_000);
                        resultMap.put(userId, new RaceAttemptResult(userId, acquired, latencyMs, null));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        resultMap.put(userId, new RaceAttemptResult(userId, false, 0, "Worker thread interrupted"));
                    } catch (Exception ex) {
                        String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                        resultMap.put(userId, new RaceAttemptResult(userId, false, 0, msg));
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            // Release all worker threads simultaneously
            startGate.countDown();

            boolean completedInTime = doneGate.await(15, TimeUnit.SECONDS);
            if (!completedInTime) {
                log.warn("Race simulation timed out waiting for all attempts to finish within 15 seconds.");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation thread was interrupted", ie);
        } finally {
            // Guarantee no worker threads remain running after simulation exits
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Some simulation worker threads did not terminate within grace period.");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            simulationRateLimiter.release();
        }

        // Reconstruct exact ordered list guaranteeing 1-to-1 results for all requested users
        List<RaceAttemptResult> attempts = new ArrayList<>(userCount);
        for (int i = 1; i <= userCount; i++) {
            String userId = "race-user-" + i;
            RaceAttemptResult result = resultMap.get(userId);
            if (result == null) {
                result = new RaceAttemptResult(userId, false, 0, "Simulation attempt timed out after 15 seconds");
            }
            attempts.add(result);
        }

        int successfulHolds = 0;
        int rejectedHolds = 0;
        int errorCount = 0;
        List<Long> latencies = new ArrayList<>();

        // Latency percentiles are calculated exclusively from non-error Redis lock roundtrips
        // (both successful acquisitions and clean lock rejections), excluding attempts that failed with exceptions or timed out.
        for (RaceAttemptResult attempt : attempts) {
            if (attempt.error() != null) {
                errorCount++;
            } else if (attempt.acquired()) {
                successfulHolds++;
                latencies.add(attempt.latencyMs());
            } else {
                rejectedHolds++;
                latencies.add(attempt.latencyMs());
            }
        }

        Collections.sort(latencies);
        double p50 = computePercentile(latencies, 50.0);
        double p95 = computePercentile(latencies, 95.0);
        double p99 = computePercentile(latencies, 99.0);

        // Scope: Exact count of confirmed booking records in PostgreSQL for the contested seat and showtime
        int confirmedBookingsInDb = (int) bookingSeatRepository.countByShowtimeIdAndSeatIdAndStatus(
                showtimeId, seatId, BookingStatus.CONFIRMED
        );

        if (successfulHolds > 0) {
            sseBroadcastService.publishSeatEvent(
                    showtimeId,
                    seatId,
                    "HELD",
                    Instant.now().plusSeconds(redisLockService.getHoldTtlSeconds())
            );
        }

        return new RaceResponse(
                showtimeId,
                seatId,
                userCount,
                successfulHolds,
                rejectedHolds,
                errorCount,
                p50,
                p95,
                p99,
                confirmedBookingsInDb,
                attempts
        );
    }

    public ResetDemoResponse resetDemoShowtime(Long showtimeId) {
        if (showtimeId == null || !DEMO_SHOWTIME_IDS.contains(showtimeId)) {
            throw new IllegalArgumentException("Reset is only permitted on designated demo showtimes: " + DEMO_SHOWTIME_IDS);
        }

        if (!simulationRateLimiter.tryAcquire()) {
            throw new IllegalStateException("Another concurrency simulation or reset is currently running. Please wait a moment.");
        }

        try {
            Showtime showtime = showtimeRepository.findById(showtimeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Demo showtime not found with ID: " + showtimeId));

            List<Seat> seats = seatRepository.findByVenueId(showtime.getVenue().getId());

            Set<Long> confirmedSeatIds = bookingSeatRepository.findByShowtimeIdAndStatus(
                    showtimeId,
                    BookingStatus.CONFIRMED
            ).stream().map(bs -> bs.getSeat().getId()).collect(Collectors.toSet());

            int releasedCount = 0;
            for (Seat seat : seats) {
                Long seatId = seat.getId();
                if (confirmedSeatIds.contains(seatId)) {
                    continue; // Preserve confirmed bookings in PostgreSQL
                }

                String currentToken = redisLockService.getHoldToken(showtimeId, seatId);
                if (currentToken != null) {
                    // Double check PostgreSQL again to ensure it wasn't confirmed
                    if (bookingSeatRepository.existsByShowtimeIdAndSeatIdAndStatus(showtimeId, seatId, BookingStatus.CONFIRMED)) {
                        continue;
                    }

                    // Atomically compare expectedToken with current stored token in Redis.
                    // Only delete the seat hold key and owner metadata if it matches, and publish AVAILABLE atomically.
                    boolean released = sseBroadcastService.releaseHoldAndPublishAvailable(showtimeId, seatId, currentToken);
                    if (released) {
                        releasedCount++;
                        log.debug("Atomically released demo hold for seat {} (token {}) on showtime {}.", seatId, currentToken, showtimeId);
                    } else {
                        log.info("Suppressed unsafe release for seat {} on showtime {}: token changed or expired.", seatId, showtimeId);
                    }
                }
            }

            return new ResetDemoResponse(
                    showtimeId,
                    releasedCount,
                    "Demo showtime " + showtimeId + " reset successfully. Cleared " + releasedCount + " temporary holds."
            );
        } finally {
            simulationRateLimiter.release();
        }
    }

    private double computePercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
