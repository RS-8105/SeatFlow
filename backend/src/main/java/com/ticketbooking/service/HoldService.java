package com.ticketbooking.service;

import com.ticketbooking.domain.BookingSeat;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.exception.HoldNotFoundOrExpiredException;
import com.ticketbooking.exception.HoldOwnershipException;
import com.ticketbooking.exception.SeatAlreadyBookedException;
import com.ticketbooking.exception.SeatAlreadyHeldException;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowtimeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class HoldService {

    private static final String HOLD_OWNER_PREFIX = "hold_owner:";

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final RedisLockService redisLockService;
    private final StringRedisTemplate redisTemplate;
    private final SseBroadcastService sseBroadcastService;
    private final long holdTtlSeconds;
    private final int maxSeatsPerUser;

    public HoldService(
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            BookingSeatRepository bookingSeatRepository,
            RedisLockService redisLockService,
            StringRedisTemplate redisTemplate,
            SseBroadcastService sseBroadcastService,
            @Value("${ticketbooking.hold.ttl-seconds:300}") long holdTtlSeconds,
            @Value("${ticketbooking.hold.max-seats-per-user:6}") int maxSeatsPerUser
    ) {
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.redisLockService = redisLockService;
        this.redisTemplate = redisTemplate;
        this.sseBroadcastService = sseBroadcastService;
        this.holdTtlSeconds = holdTtlSeconds;
        this.maxSeatsPerUser = maxSeatsPerUser;
    }

    public HoldResult createHold(Long showtimeId, Collection<Long> seatIds, String userId) {
        validateHoldRequestInputs(showtimeId, seatIds, userId);

        Set<Long> uniqueSeatIds = new LinkedHashSet<>(seatIds);

        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found with ID: " + showtimeId));

        List<Seat> seats = seatRepository.findAllByIdIn(uniqueSeatIds);
        if (seats.size() != uniqueSeatIds.size()) {
            throw new IllegalArgumentException("One or more requested seats do not exist");
        }

        Long expectedVenueId = showtime.getVenue().getId();
        for (Seat seat : seats) {
            if (!expectedVenueId.equals(seat.getVenue().getId())) {
                throw new IllegalArgumentException("Seat " + seat.getId() + " does not belong to venue " + expectedVenueId);
            }
        }

        List<BookingSeat> confirmedSeats = bookingSeatRepository.findByShowtimeIdAndSeatIdInAndStatus(
                showtimeId,
                uniqueSeatIds,
                BookingStatus.CONFIRMED
        );
        if (!confirmedSeats.isEmpty()) {
            List<Long> confirmedSeatIds = confirmedSeats.stream()
                    .map(bs -> bs.getSeat().getId())
                    .toList();
            throw new SeatAlreadyBookedException("Seats already confirmed for this showtime: " + confirmedSeatIds);
        }

        String holdToken = UUID.randomUUID().toString();

        boolean acquired = redisLockService.acquireMultiHold(
                showtimeId,
                uniqueSeatIds,
                holdToken,
                this.holdTtlSeconds
        );

        if (!acquired) {
            throw new SeatAlreadyHeldException("One or more requested seats are currently held by another user");
        }

        redisTemplate.opsForValue().set(
                buildHoldOwnerKey(holdToken),
                userId,
                Duration.ofSeconds(this.holdTtlSeconds)
        );

        Instant expiresAt = Instant.now().plusSeconds(this.holdTtlSeconds);

        for (Long seatId : uniqueSeatIds) {
            sseBroadcastService.publishSeatEvent(showtimeId, seatId, "HELD", expiresAt);
        }

        return new HoldResult(
                holdToken,
                showtimeId,
                Collections.unmodifiableSet(uniqueSeatIds),
                userId,
                expiresAt
        );
    }

    public HoldReleaseResult releaseHold(Long showtimeId, Collection<Long> seatIds, String holdToken, String userId) {
        validateReleaseRequestInputs(showtimeId, seatIds, holdToken, userId);

        String ownerKey = buildHoldOwnerKey(holdToken);
        String recordedOwner = redisTemplate.opsForValue().get(ownerKey);

        if (recordedOwner == null) {
            throw new HoldNotFoundOrExpiredException("Hold has expired or does not exist for token: " + holdToken);
        }

        if (!recordedOwner.equals(userId)) {
            throw new HoldOwnershipException("User " + userId + " does not own hold token: " + holdToken);
        }

        Set<Long> releasedSeats = new HashSet<>();
        for (Long seatId : seatIds) {
            boolean released = redisLockService.releaseHold(showtimeId, seatId, holdToken);
            if (released) {
                releasedSeats.add(seatId);
                sseBroadcastService.publishSeatEvent(showtimeId, seatId, "AVAILABLE", null);
            }
        }

        boolean fullyReleased = releasedSeats.size() == seatIds.size();
        if (fullyReleased) {
            redisTemplate.delete(ownerKey);
        }

        return new HoldReleaseResult(
                holdToken,
                showtimeId,
                Collections.unmodifiableSet(releasedSeats),
                fullyReleased
        );
    }

    public boolean isHoldValid(Long showtimeId, Collection<Long> seatIds, String holdToken, String userId) {
        if (showtimeId == null || seatIds == null || seatIds.isEmpty() || holdToken == null || userId == null) {
            return false;
        }

        String recordedOwner = redisTemplate.opsForValue().get(buildHoldOwnerKey(holdToken));
        if (recordedOwner == null || !recordedOwner.equals(userId)) {
            return false;
        }

        for (Long seatId : seatIds) {
            if (!redisLockService.validateHoldOwnership(showtimeId, seatId, holdToken)) {
                return false;
            }
        }

        return true;
    }

    public long getHoldTtlSeconds() {
        return holdTtlSeconds;
    }

    public int getMaxSeatsPerUser() {
        return maxSeatsPerUser;
    }

    private String buildHoldOwnerKey(String holdToken) {
        return HOLD_OWNER_PREFIX + holdToken;
    }

    private void validateHoldRequestInputs(Long showtimeId, Collection<Long> seatIds, String userId) {
        if (showtimeId == null) {
            throw new IllegalArgumentException("Showtime ID must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat IDs collection must not be null or empty");
        }
        if (seatIds.size() > maxSeatsPerUser) {
            throw new IllegalArgumentException(
                    "Requested seat count (" + seatIds.size() + ") exceeds maximum limit (" + maxSeatsPerUser + ")"
            );
        }

        Set<Long> checkDuplicates = new HashSet<>();
        for (Long seatId : seatIds) {
            if (seatId == null) {
                throw new IllegalArgumentException("Seat ID inside collection must not be null");
            }
            if (!checkDuplicates.add(seatId)) {
                throw new IllegalArgumentException("Duplicate seat ID detected in request: " + seatId);
            }
        }
    }

    private void validateReleaseRequestInputs(Long showtimeId, Collection<Long> seatIds, String holdToken, String userId) {
        if (showtimeId == null) {
            throw new IllegalArgumentException("Showtime ID must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank");
        }
        if (holdToken == null || holdToken.isBlank()) {
            throw new IllegalArgumentException("Hold token must not be null or blank");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat IDs collection must not be null or empty");
        }
    }
}
