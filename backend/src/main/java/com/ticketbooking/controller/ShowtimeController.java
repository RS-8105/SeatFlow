package com.ticketbooking.controller;

import com.ticketbooking.domain.BookingSeat;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.dto.CreateHoldRequest;
import com.ticketbooking.dto.HoldResponse;
import com.ticketbooking.dto.SeatResponse;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowtimeRepository;
import com.ticketbooking.security.SecurityUtils;
import com.ticketbooking.service.HoldResult;
import com.ticketbooking.service.HoldService;
import com.ticketbooking.service.RedisLockService;
import com.ticketbooking.service.SseBroadcastService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final RedisLockService redisLockService;
    private final HoldService holdService;
    private final SseBroadcastService sseBroadcastService;

    public ShowtimeController(
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            BookingSeatRepository bookingSeatRepository,
            RedisLockService redisLockService,
            HoldService holdService,
            SseBroadcastService sseBroadcastService
    ) {
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.redisLockService = redisLockService;
        this.holdService = holdService;
        this.sseBroadcastService = sseBroadcastService;
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> getSeatsForShowtime(@PathVariable Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + id));

        List<Seat> seats = seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(showtime.getVenue().getId());

        Set<Long> confirmedSeatIds = bookingSeatRepository.findByShowtimeIdAndStatus(id, BookingStatus.CONFIRMED).stream()
                .map(bs -> bs.getSeat().getId())
                .collect(Collectors.toSet());

        return seats.stream().map(seat -> {
            String status;
            if (confirmedSeatIds.contains(seat.getId())) {
                status = "BOOKED";
            } else if (redisLockService.isSeatHeld(id, seat.getId())) {
                status = "HELD";
            } else {
                status = "AVAILABLE";
            }

            return new SeatResponse(
                    seat.getId(),
                    seat.getRowLabel(),
                    seat.getSeatNumber(),
                    seat.getCategory(),
                    seat.getPrice(),
                    status
            );
        }).toList();
    }

    @PostMapping("/{id}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(
            @PathVariable Long id,
            @Valid @RequestBody CreateHoldRequest request,
            Authentication authentication
    ) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        HoldResult result = holdService.createHold(id, request.seatIds(), authenticatedUserId);
        return new HoldResponse(
                result.holdToken(),
                result.showtimeId(),
                result.seatIds(),
                result.userId(),
                result.expiresAt()
        );
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeatUpdates(@PathVariable Long id) {
        if (!showtimeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Showtime not found with ID: " + id);
        }
        return sseBroadcastService.subscribe(id);
    }
}
