package com.ticketbooking.repository;

import com.ticketbooking.domain.Showtime;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    @Override
    @EntityGraph(attributePaths = {"event", "venue"})
    java.util.Optional<Showtime> findById(Long id);

    @EntityGraph(attributePaths = {"event", "venue"})
    List<Showtime> findByEventId(Long eventId);

    List<Showtime> findByVenueId(Long venueId);

    List<Showtime> findByEventIdAndVenueId(Long eventId, Long venueId);
}
