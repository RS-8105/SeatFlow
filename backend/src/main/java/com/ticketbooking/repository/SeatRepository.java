package com.ticketbooking.repository;

import com.ticketbooking.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByVenueId(Long venueId);

    List<Seat> findByVenueIdOrderByRowLabelAscSeatNumberAsc(Long venueId);

    List<Seat> findAllByIdIn(Collection<Long> seatIds);
}
