package com.ticketbooking.repository;

import com.ticketbooking.domain.BookingSeat;
import com.ticketbooking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findByBookingId(Long bookingId);

    boolean existsByShowtimeIdAndSeatIdAndStatus(Long showtimeId, Long seatId, BookingStatus status);

    long countByShowtimeIdAndSeatIdAndStatus(Long showtimeId, Long seatId, BookingStatus status);

    List<BookingSeat> findByShowtimeIdAndStatus(Long showtimeId, BookingStatus status);

    List<BookingSeat> findByShowtimeIdAndSeatIdInAndStatus(Long showtimeId, Collection<Long> seatIds, BookingStatus status);
}
