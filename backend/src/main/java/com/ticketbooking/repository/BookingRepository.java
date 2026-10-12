package com.ticketbooking.repository;

import com.ticketbooking.domain.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"bookingSeats", "bookingSeats.seat", "showtime", "showtime.event", "showtime.venue"})
    List<Booking> findByUserId(String userId);

    @Override
    @EntityGraph(attributePaths = {"bookingSeats", "bookingSeats.seat", "showtime", "showtime.event", "showtime.venue"})
    Optional<Booking> findById(Long id);

    @EntityGraph(attributePaths = {"bookingSeats", "bookingSeats.seat", "showtime", "showtime.event", "showtime.venue"})
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdAndUserId(Long id, String userId);

    List<Booking> findByShowtimeId(Long showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"bookingSeats", "bookingSeats.seat", "showtime", "showtime.event", "showtime.venue"})
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") Long id);
}
