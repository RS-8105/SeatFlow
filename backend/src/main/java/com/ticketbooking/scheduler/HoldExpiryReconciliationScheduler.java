package com.ticketbooking.scheduler;

import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowtimeRepository;
import com.ticketbooking.service.RedisLockService;
import com.ticketbooking.service.SseBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class HoldExpiryReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryReconciliationScheduler.class);

    private final SseBroadcastService sseBroadcastService;
    private final RedisLockService redisLockService;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;

    // Track active hold tokens per showtime: showtimeId -> (seatId -> holdToken)
    private final Map<Long, Map<Long, String>> trackedHeldTokens = new ConcurrentHashMap<>();

    public HoldExpiryReconciliationScheduler(
            SseBroadcastService sseBroadcastService,
            RedisLockService redisLockService,
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            BookingSeatRepository bookingSeatRepository
    ) {
        this.sseBroadcastService = sseBroadcastService;
        this.redisLockService = redisLockService;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.bookingSeatRepository = bookingSeatRepository;
    }

    @Scheduled(fixedRate = 30000)
    public void reconcileExpiredHolds() {
        Set<Long> activeShowtimeIds = sseBroadcastService.getActiveShowtimeIds();
        if (activeShowtimeIds.isEmpty()) {
            return;
        }

        for (Long showtimeId : activeShowtimeIds) {
            try {
                reconcileShowtime(showtimeId);
            } catch (Exception ex) {
                log.error("Error reconciling expired holds for showtime {}: {}", showtimeId, ex.getMessage(), ex);
            }
        }
    }

    private void reconcileShowtime(Long showtimeId) {
        Optional<Showtime> showtimeOpt = showtimeRepository.findById(showtimeId);
        if (showtimeOpt.isEmpty()) {
            return;
        }

        Showtime showtime = showtimeOpt.get();
        List<Seat> seats = seatRepository.findByVenueId(showtime.getVenue().getId());

        Set<Long> confirmedSeatIds = bookingSeatRepository.findByShowtimeIdAndStatus(showtimeId, BookingStatus.CONFIRMED).stream()
                .map(bs -> bs.getSeat().getId())
                .collect(Collectors.toSet());

        Map<Long, String> previouslyHeld = trackedHeldTokens.getOrDefault(showtimeId, Collections.emptyMap());
        Map<Long, String> currentlyHeld = new ConcurrentHashMap<>();

        for (Seat seat : seats) {
            Long seatId = seat.getId();
            if (confirmedSeatIds.contains(seatId)) {
                // Seat confirmed in PostgreSQL: BOOKED state takes precedence
                continue;
            }

            String currentToken = redisLockService.getHoldToken(showtimeId, seatId);
            if (currentToken != null) {
                // Seat is actively held in Redis
                currentlyHeld.put(seatId, currentToken);
                if (previouslyHeld.containsKey(seatId) && !currentToken.equals(previouslyHeld.get(seatId))) {
                    log.debug("Showtime {} seat {} was reacquired with new token {}. Preserving HELD state.",
                            showtimeId, seatId, currentToken);
                }
            } else if (previouslyHeld.containsKey(seatId)) {
                String expiredToken = previouslyHeld.get(seatId);

                // Double check PostgreSQL in case booking was confirmed right at expiration
                if (bookingSeatRepository.existsByShowtimeIdAndSeatIdAndStatus(showtimeId, seatId, BookingStatus.CONFIRMED)) {
                    continue;
                }

                // Atomically verify that the seat is STILL unheld in Redis at the exact moment of publication.
                // This prevents a race where a new user acquires a hold between our check and our broadcast.
                boolean published = sseBroadcastService.publishAvailableIfUnheld(showtimeId, seatId);
                if (published) {
                    log.info("Detected hold expiry for showtime {} seat {} (token {}). Atomically broadcasted AVAILABLE.",
                            showtimeId, seatId, expiredToken);
                } else {
                    // Key was reacquired in Redis concurrently; do not publish stale AVAILABLE!
                    String reacquiredToken = redisLockService.getHoldToken(showtimeId, seatId);
                    if (reacquiredToken != null) {
                        currentlyHeld.put(seatId, reacquiredToken);
                        log.info("Suppressed stale AVAILABLE for showtime {} seat {}: reacquired with token {}.",
                                showtimeId, seatId, reacquiredToken);
                    }
                }
            }
        }

        trackedHeldTokens.put(showtimeId, currentlyHeld);
    }
}
