package com.ticketbooking.repository;

import com.ticketbooking.domain.Payment;
import com.ticketbooking.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);

    List<Payment> findByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
