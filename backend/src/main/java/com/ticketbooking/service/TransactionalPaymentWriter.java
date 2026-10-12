package com.ticketbooking.service;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Payment;
import com.ticketbooking.domain.PaymentStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class TransactionalPaymentWriter {

    private static final Logger log = LoggerFactory.getLogger(TransactionalPaymentWriter.class);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public TransactionalPaymentWriter(BookingRepository bookingRepository, PaymentRepository paymentRepository) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public Payment persistPayment(Long bookingId, String userId, boolean simulateSuccess) {
        validateInputs(bookingId, userId);

        // Acquire exclusive row lock on the booking row to serialize concurrent payment attempts for the same booking
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with ID: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new com.ticketbooking.exception.HoldOwnershipException("User " + userId + " does not own booking " + bookingId);
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot process payment for a cancelled booking: " + bookingId);
        }

        // Check if an existing successful payment already exists for this booking (idempotency guard)
        List<Payment> existingSuccessfulPayments = paymentRepository.findByBookingIdAndStatus(
                bookingId,
                PaymentStatus.SUCCESS
        );
        if (!existingSuccessfulPayments.isEmpty()) {
            log.info("Booking {} already has a successful payment record. Returning existing payment.", bookingId);
            return existingSuccessfulPayments.get(0);
        }

        PaymentStatus targetStatus = simulateSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        Payment payment = new Payment(booking, targetStatus, true);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        log.info("Simulated payment {} for booking {} with amount {} and status {}",
                savedPayment.getId(), bookingId, booking.getTotalAmount(), targetStatus);

        return savedPayment;
    }

    private void validateInputs(Long bookingId, String userId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank");
        }
    }
}
