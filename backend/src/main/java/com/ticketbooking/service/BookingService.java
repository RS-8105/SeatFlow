package com.ticketbooking.service;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingSeat;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.exception.HoldNotFoundOrExpiredException;
import com.ticketbooking.exception.IdempotencyConflictException;
import com.ticketbooking.exception.SeatAlreadyBookedException;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowtimeRepository;
import com.ticketbooking.exception.HoldOwnershipException;
import com.ticketbooking.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final HoldService holdService;
    private final TransactionalBookingWriter transactionalBookingWriter;
    private final SseBroadcastService sseBroadcastService;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            HoldService holdService,
            TransactionalBookingWriter transactionalBookingWriter,
            SseBroadcastService sseBroadcastService
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.holdService = holdService;
        this.transactionalBookingWriter = transactionalBookingWriter;
        this.sseBroadcastService = sseBroadcastService;
    }

    public Booking confirmBooking(
            Long showtimeId,
            Collection<Long> seatIds,
            String holdToken,
            String userId,
            String idempotencyKey
    ) {
        validateConfirmInputs(showtimeId, seatIds, holdToken, userId, idempotencyKey);

        Set<Long> uniqueSeatIds = new LinkedHashSet<>(seatIds);

        // 1. Check existing idempotency key before attempting new operation
        Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existingBooking.isPresent()) {
            Booking existing = existingBooking.get();
            validateIdempotencyCompatibility(existing, showtimeId, uniqueSeatIds, userId);
            return existing;
        }

        // 2. Validate hold existence and user ownership in Redis
        boolean validHold = holdService.isHoldValid(showtimeId, uniqueSeatIds, holdToken, userId);
        if (!validHold) {
            throw new HoldNotFoundOrExpiredException(
                    "Hold has expired or does not belong to user for token: " + holdToken
            );
        }

        // 3. Verify showtime and venue seats
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found with ID: " + showtimeId));

        List<Seat> seats = seatRepository.findAllByIdIn(uniqueSeatIds);
        if (seats.size() != uniqueSeatIds.size()) {
            throw new IllegalArgumentException("One or more requested seats do not exist");
        }

        Long venueId = showtime.getVenue().getId();
        for (Seat seat : seats) {
            if (!venueId.equals(seat.getVenue().getId())) {
                throw new IllegalArgumentException(
                        "Seat " + seat.getId() + " does not belong to venue " + venueId
                );
            }
        }

        // 4. PostgreSQL check for confirmed bookings
        List<BookingSeat> confirmedSeats = bookingSeatRepository.findByShowtimeIdAndSeatIdInAndStatus(
                showtimeId,
                uniqueSeatIds,
                BookingStatus.CONFIRMED
        );
        if (!confirmedSeats.isEmpty()) {
            List<Long> confirmedIds = confirmedSeats.stream()
                    .map(bs -> bs.getSeat().getId())
                    .toList();
            throw new SeatAlreadyBookedException("Seats already confirmed in database: " + confirmedIds);
        }

        // 5. Calculate total amount
        BigDecimal totalAmount = seats.stream()
                .map(Seat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 6. Persist within transactional boundary with post-commit lock release
        return transactionalBookingWriter.persistBooking(
                showtime,
                seats,
                userId,
                idempotencyKey,
                totalAmount,
                holdToken,
                uniqueSeatIds
        );
    }

    private void validateConfirmInputs(
            Long showtimeId,
            Collection<Long> seatIds,
            String holdToken,
            String userId,
            String idempotencyKey
    ) {
        if (showtimeId == null) {
            throw new IllegalArgumentException("Showtime ID must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank");
        }
        if (holdToken == null || holdToken.isBlank()) {
            throw new IllegalArgumentException("Hold token must not be null or blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be null or blank");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat IDs collection must not be null or empty");
        }
    }

    private void validateIdempotencyCompatibility(
            Booking existing,
            Long requestedShowtimeId,
            Collection<Long> requestedSeatIds,
            String requestedUserId
    ) {
        if (!existing.getUserId().equals(requestedUserId)) {
            throw new IdempotencyConflictException(
                    "Idempotency key reused for a different user: " + existing.getIdempotencyKey()
            );
        }
        if (!existing.getShowtime().getId().equals(requestedShowtimeId)) {
            throw new IdempotencyConflictException(
                    "Idempotency key reused for a different showtime: " + existing.getIdempotencyKey()
            );
        }
        Set<Long> existingSeatIds = existing.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .collect(Collectors.toSet());
        if (!existingSeatIds.equals(Set.copyOf(requestedSeatIds))) {
            throw new IdempotencyConflictException(
                    "Idempotency key reused with differing seat selection: " + existing.getIdempotencyKey()
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Booking cancelBooking(Long bookingId, String userId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank");
        }

        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new HoldOwnershipException("User " + userId + " does not own booking " + bookingId);
        }

        // Idempotency: repeated cancellation returns current cancelled booking
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        for (BookingSeat bookingSeat : booking.getBookingSeats()) {
            bookingSeat.setStatus(BookingStatus.CANCELLED);
        }

        Booking saved = bookingRepository.save(booking);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (BookingSeat bookingSeat : saved.getBookingSeats()) {
                        sseBroadcastService.publishSeatEvent(
                                saved.getShowtime().getId(),
                                bookingSeat.getSeat().getId(),
                                "AVAILABLE",
                                null
                        );
                    }
                }
            });
        }

        return saved;
    }
}
