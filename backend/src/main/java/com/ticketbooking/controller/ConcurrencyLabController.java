package com.ticketbooking.controller;

import com.ticketbooking.dto.RaceRequest;
import com.ticketbooking.dto.RaceResponse;
import com.ticketbooking.dto.ResetDemoRequest;
import com.ticketbooking.dto.ResetDemoResponse;
import com.ticketbooking.service.ConcurrencyLabService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class ConcurrencyLabController {

    private final ConcurrencyLabService concurrencyLabService;

    public ConcurrencyLabController(ConcurrencyLabService concurrencyLabService) {
        this.concurrencyLabService = concurrencyLabService;
    }

    @PostMapping("/race")
    public RaceResponse simulateRace(@Valid @RequestBody RaceRequest request) {
        return concurrencyLabService.simulateRace(
                request.showtimeId(),
                request.seatId(),
                request.userCount()
        );
    }

    @PostMapping("/reset")
    public ResetDemoResponse resetDemo(@Valid @RequestBody ResetDemoRequest request) {
        return concurrencyLabService.resetDemoShowtime(request.showtimeId());
    }
}
