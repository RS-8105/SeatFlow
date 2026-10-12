package com.ticketbooking.controller;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.Payment;
import com.ticketbooking.domain.Seat;
import com.ticketbooking.domain.Showtime;
import com.ticketbooking.dto.BookingResponse;
import com.ticketbooking.dto.ConfirmBookingRequest;
import com.ticketbooking.dto.PaymentRequest;
import com.ticketbooking.dto.PaymentResponse;
import com.ticketbooking.exception.HoldOwnershipException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.security.SecurityUtils;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

    public BookingController(
            BookingService bookingService,
            BookingRepository bookingRepository,
            PaymentService paymentService
    ) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
    }

    @PostMapping("/api/holds/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse confirmHold(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConfirmBookingRequest request,
            Authentication authentication
    ) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        Booking booking = bookingService.confirmBooking(
                request.showtimeId(),
                request.seatIds(),
                request.holdToken(),
                authenticatedUserId,
                idempotencyKey
        );
        return toBookingResponse(booking);
    }

    @GetMapping("/api/bookings/{id}")
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));

        if (!booking.getUserId().equals(authenticatedUserId)) {
            throw new HoldOwnershipException("User " + authenticatedUserId + " does not own booking " + id);
        }

        return toBookingResponse(booking);
    }

    @GetMapping("/api/bookings/me")
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Authentication authentication) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        return bookingRepository.findByUserId(authenticatedUserId).stream()
                .map(this::toBookingResponse)
                .toList();
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        Booking cancelledBooking = bookingService.cancelBooking(id, authenticatedUserId);
        return toBookingResponse(cancelledBooking);
    }

    @PostMapping("/api/bookings/{id}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse payForBooking(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication
    ) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        boolean simulateSuccess = request.simulateSuccess() == null || request.simulateSuccess();
        Payment payment = paymentService.processPayment(id, authenticatedUserId, simulateSuccess);
        return toPaymentResponse(payment);
    }

    @GetMapping("/api/bookings/{id}/payments")
    @Transactional(readOnly = true)
    public List<PaymentResponse> getBookingPayments(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String authenticatedUserId = SecurityUtils.getCanonicalUserId(authentication);
        return paymentService.getPaymentsForBooking(id, authenticatedUserId).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        Showtime showtime = booking.getShowtime();
        String eventName = null;
        String venueName = null;
        String venueCity = null;
        Instant showtimeStartsAt = null;
        Long showtimeId = null;

        if (showtime != null) {
            showtimeId = showtime.getId();
            showtimeStartsAt = showtime.getStartsAt();
            if (showtime.getEvent() != null) {
                eventName = showtime.getEvent().getName();
            }
            if (showtime.getVenue() != null) {
                venueName = showtime.getVenue().getName();
                venueCity = showtime.getVenue().getCity();
            }
        }

        List<BookingResponse.BookingSeatDetail> seats = booking.getBookingSeats() == null ? List.of() :
                booking.getBookingSeats().stream()
                        .map(bs -> {
                            Seat seat = bs.getSeat();
                            Long seatId = seat != null ? seat.getId() : null;
                            String rowLabel = seat != null ? seat.getRowLabel() : null;
                            Integer seatNumber = seat != null ? seat.getSeatNumber() : null;
                            String seatLabel = (rowLabel != null && seatNumber != null)
                                    ? (rowLabel + seatNumber)
                                    : (seatId != null ? "SEAT #" + seatId : null);
                            String category = seat != null ? seat.getCategory() : null;
                            BigDecimal price = seat != null ? seat.getPrice() : null;
                            return new BookingResponse.BookingSeatDetail(
                                    seatId,
                                    rowLabel,
                                    seatNumber,
                                    seatLabel,
                                    category,
                                    price
                            );
                        })
                        .toList();

        List<Long> seatIds = booking.getBookingSeats() == null ? List.of() :
                booking.getBookingSeats().stream()
                        .map(bs -> bs.getSeat() != null ? bs.getSeat().getId() : null)
                        .filter(java.util.Objects::nonNull)
                        .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                showtimeId,
                eventName,
                venueName,
                venueCity,
                showtimeStartsAt,
                booking.getStatus(),
                booking.getIdempotencyKey(),
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                seatIds,
                seats
        );
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getStatus(),
                payment.isSimulated(),
                payment.getCreatedAt()
        );
    }
}
