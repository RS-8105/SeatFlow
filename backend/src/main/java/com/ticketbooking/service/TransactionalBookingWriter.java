package com.ticketbooking.service;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingSeat;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.exception.IdempotencyConflictException;
import com.ticketbooking.exception.SeatAlreadyBookedException;
import com.ticketbooking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
public class TransactionalBookingWriter {

    private static final Logger log = LoggerFactory.getLogger(TransactionalBookingWriter.class);

    private final BookingRepository bookingRepository;
    private final HoldService holdService;
    private final SseBroadcastService sseBroadcastService;

    public TransactionalBookingWriter(
            BookingRepository bookingRepository,
            HoldService holdService,
            SseBroadcastService sseBroadcastService
    ) {
        this.bookingRepository = bookingRepository;
        this.holdService = holdService;
        this.sseBroadcastService = sseBroadcastService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Booking persistBooking(
            Showtime showtime,
            List<Seat> seats,
            String userId,
            String idempotencyKey,
            BigDecimal totalAmount,
            String holdToken,
            Collection<Long> seatIds
    ) {
        Booking booking = new Booking(userId, showtime, BookingStatus.CONFIRMED, idempotencyKey, totalAmount);

        for (Seat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat(booking, showtime, seat, BookingStatus.CONFIRMED);
            booking.addBookingSeat(bookingSeat);
        }

        try {
            Booking savedBooking = bookingRepository.saveAndFlush(booking);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            holdService.releaseHold(showtime.getId(), seatIds, holdToken, userId);
                            log.info("Successfully released Redis holds for booking ID {}", savedBooking.getId());
                        } catch (Exception ex) {
                            log.error("Recoverable lock-release failure after DB commit for booking ID {}: {}",
                                    savedBooking.getId(), ex.getMessage(), ex);
                        }

                        for (Long seatId : seatIds) {
                            sseBroadcastService.publishSeatEvent(showtime.getId(), seatId, "BOOKED", null);
                        }
                    }
                });
            }

            return savedBooking;
        } catch (DataIntegrityViolationException dive) {
            String message = dive.getMessage() != null ? dive.getMessage().toLowerCase() : "";

            if (message.contains("idx_unique_confirmed_seat_showtime") || message.contains("unique")) {
                // Check if this was a concurrent idempotency collision
                var existingOpt = bookingRepository.findByIdempotencyKey(idempotencyKey);
                if (existingOpt.isPresent()) {
                    Booking existing = existingOpt.get();
                    validateCompatibility(existing, showtime.getId(), seatIds, userId);
                    return existing;
                }
                throw new SeatAlreadyBookedException("One or more seats have already been confirmed by another transaction: " + dive.getMessage());
            }
            throw dive;
        }
    }

    private void validateCompatibility(Booking existing, Long requestedShowtimeId, Collection<Long> requestedSeatIds, String requestedUserId) {
        if (!existing.getUserId().equals(requestedUserId)) {
            throw new IdempotencyConflictException("Idempotency key reused for a different user: " + existing.getIdempotencyKey());
        }
        if (!existing.getShowtime().getId().equals(requestedShowtimeId)) {
            throw new IdempotencyConflictException("Idempotency key reused for a different showtime: " + existing.getIdempotencyKey());
        }
        Set<Long> existingSeatIds = existing.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .collect(java.util.stream.Collectors.toSet());
        if (!existingSeatIds.equals(Set.copyOf(requestedSeatIds))) {
            throw new IdempotencyConflictException("Idempotency key reused with differing seat selection: " + existing.getIdempotencyKey());
        }
    }
}
