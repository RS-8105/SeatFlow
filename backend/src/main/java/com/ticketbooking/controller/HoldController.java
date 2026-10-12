package com.ticketbooking.controller;

import com.ticketbooking.dto.HoldReleaseResponse;
import com.ticketbooking.dto.ReleaseHoldRequest;
import com.ticketbooking.security.SecurityUtils;
import com.ticketbooking.service.HoldReleaseResult;
import com.ticketbooking.service.HoldService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/holds")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @DeleteMapping
    public HoldReleaseResponse releaseHold(
            @Valid @RequestBody ReleaseHoldRequest request,
            Authentication authentication
    ) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        HoldReleaseResult result = holdService.releaseHold(
                request.showtimeId(),
                request.seatIds(),
                request.holdToken(),
                authenticatedUserId
        );

        return new HoldReleaseResponse(
                result.holdToken(),
                result.showtimeId(),
                result.releasedSeatIds(),
                result.fullyReleased()
        );
    }
}
