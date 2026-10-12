package com.ticketbooking.controller;

import com.ticketbooking.domain.Event;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.dto.EventResponse;
import com.ticketbooking.dto.ShowtimeResponse;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.ShowtimeRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@Transactional(readOnly = true)
public class EventController {

    private final EventRepository eventRepository;
    private final ShowtimeRepository showtimeRepository;

    public EventController(EventRepository eventRepository, ShowtimeRepository showtimeRepository) {
        this.eventRepository = eventRepository;
        this.showtimeRepository = showtimeRepository;
    }

    @GetMapping
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::toEventResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public EventResponse getEventById(@PathVariable Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
        return toEventResponse(event);
    }

    @GetMapping("/{id}/showtimes")
    public List<ShowtimeResponse> getShowtimesForEvent(@PathVariable Long id) {
        if (!eventRepository.existsById(id)) {
            throw new ResourceNotFoundException("Event not found with ID: " + id);
        }

        return showtimeRepository.findByEventId(id).stream()
                .map(this::toShowtimeResponse)
                .toList();
    }

    private EventResponse toEventResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getCreatedAt()
        );
    }

    private ShowtimeResponse toShowtimeResponse(Showtime showtime) {
        return new ShowtimeResponse(
                showtime.getId(),
                showtime.getEvent().getId(),
                showtime.getEvent().getName(),
                showtime.getVenue().getId(),
                showtime.getVenue().getName(),
                showtime.getVenue().getCity(),
                showtime.getStartsAt()
        );
    }
}
