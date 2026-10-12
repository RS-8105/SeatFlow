package com.ticketbooking.service;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.Payment;
import com.ticketbooking.domain.PaymentStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String UNIQUE_PAYMENT_CONSTRAINT = "idx_unique_successful_payment_booking";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionalPaymentWriter transactionalPaymentWriter;

    public PaymentService(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            TransactionalPaymentWriter transactionalPaymentWriter
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.transactionalPaymentWriter = transactionalPaymentWriter;
    }

    public Payment processPayment(Long bookingId, String userId) {
        return processPayment(bookingId, userId, true);
    }

    public Payment processPayment(Long bookingId, String userId, boolean simulateSuccess) {
        try {
            return transactionalPaymentWriter.persistPayment(bookingId, userId, simulateSuccess);
        } catch (DataIntegrityViolationException dive) {
            if (isSuccessfulPaymentConstraintViolation(dive)) {
                log.info("Concurrent payment collided on unique successful payment constraint for booking ID {}. Returning existing record.", bookingId);
                return paymentRepository.findByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESS).stream()
                        .findFirst()
                        .orElseThrow(() -> dive);
            }
            throw dive;
        }
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsForBooking(Long bookingId, String userId) {
        validateInputs(bookingId, userId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.ticketbooking.exception.ResourceNotFoundException("Booking not found with ID: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new com.ticketbooking.exception.HoldOwnershipException("User " + userId + " does not own booking " + bookingId);
        }

        return paymentRepository.findByBookingId(bookingId);
    }

    private boolean isSuccessfulPaymentConstraintViolation(DataIntegrityViolationException dive) {
        Throwable cause = dive;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.toLowerCase().contains(UNIQUE_PAYMENT_CONSTRAINT.toLowerCase())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
