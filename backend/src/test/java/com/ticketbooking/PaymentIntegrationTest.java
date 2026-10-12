package com.ticketbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Payment;
import com.ticketbooking.domain.PaymentStatus;
import com.ticketbooking.domain.User;
import com.ticketbooking.dto.ConfirmBookingRequest;
import com.ticketbooking.dto.CreateHoldRequest;
import com.ticketbooking.dto.PaymentRequest;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.PaymentRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.security.JwtService;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.HoldResult;
import com.ticketbooking.service.HoldService;
import com.ticketbooking.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TicketBookingApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long SHOWTIME_ID = 1L;

    @BeforeEach
    @AfterEach
    public void cleanupState() {
        // Clear isolated test Redis DB (database 1)
        try {
            Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection()
                    .serverCommands()
                    .flushDb();
        } catch (Exception ignored) {
        }

        // Clean test database entities in dependent order
        try {
            paymentRepository.deleteAll();
            bookingSeatRepository.deleteAll();
            bookingRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // 1. VALID PAYMENT PROCESSING
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Valid payment request for an eligible booking succeeds and persists state")
    public void testValidPaymentSucceedsAndPersistsState() throws Exception {
        User user = createTestUser("pay_valid_user");
        String token = jwtService.generateToken(user);

        Booking booking = createConfirmedBooking(user, List.of(1L));

        // Submit valid payment
        PaymentRequest paymentRequest = new PaymentRequest(null, true);
        MvcResult result = mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.bookingId").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        long paymentId = responseJson.get("id").asLong();

        // Verify database persistence in paymentRepository
        Payment savedPayment = paymentRepository.findById(paymentId).orElse(null);
        assertThat(savedPayment).isNotNull();
        assertThat(savedPayment.getBooking().getId()).isEqualTo(booking.getId());
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(savedPayment.isSimulated()).isTrue();

        // Verify booking state in bookingRepository remains CONFIRMED
        Booking persistedBooking = bookingRepository.findById(booking.getId()).orElse(null);
        assertThat(persistedBooking).isNotNull();
        assertThat(persistedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(persistedBooking.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
    }

    @Test
    @Order(2)
    @DisplayName("2a. Payment with null simulateSuccess defaults to true")
    public void testPaymentDefaultsSimulateSuccessToTrue() throws Exception {
        User user = createTestUser("pay_default_user");
        String token = jwtService.generateToken(user);

        Booking booking = createConfirmedBooking(user, List.of(2L));

        // Request with null simulateSuccess
        PaymentRequest paymentRequest = new PaymentRequest(null, null);
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.simulated").value(true));

        List<Payment> payments = paymentRepository.findByBookingId(booking.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    // =========================================================================
    // 2. INELIGIBLE BOOKINGS & CANCELLATIONS
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("2b. Payment for a cancelled booking is rejected without corrupting records")
    public void testPaymentForCancelledBookingIsRejected() throws Exception {
        User user = createTestUser("pay_cancelled_user");
        String token = jwtService.generateToken(user);

        Booking booking = createConfirmedBooking(user, List.of(3L));

        // Cancel the booking first
        bookingService.cancelBooking(booking.getId(), String.valueOf(user.getId()));

        // Attempt payment on cancelled booking
        PaymentRequest paymentRequest = new PaymentRequest(null, true);
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Cannot process payment for a cancelled booking")));

        // Verify zero payments persisted in database
        List<Payment> payments = paymentRepository.findByBookingId(booking.getId());
        assertThat(payments).isEmpty();

        // Verify booking status remains CANCELLED
        Booking refreshedBooking = bookingRepository.findById(booking.getId()).orElse(null);
        assertThat(refreshedBooking).isNotNull();
        assertThat(refreshedBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @Order(4)
    @DisplayName("2c. Payment for unknown booking ID returns 404 Not Found")
    public void testPaymentForUnknownBookingReturns404() throws Exception {
        User user = createTestUser("pay_unknown_user");
        String token = jwtService.generateToken(user);

        PaymentRequest paymentRequest = new PaymentRequest(null, true);
        mockMvc.perform(post("/api/bookings/999999/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Booking not found with ID: 999999")));
    }

    // =========================================================================
    // 3. IDEMPOTENCY CONTRACT
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("3. Repeated payment requests behave consistently with idempotency contract")
    public void testRepeatedPaymentRequestsAreIdempotent() throws Exception {
        User user = createTestUser("pay_idemp_user");
        String token = jwtService.generateToken(user);

        Booking booking = createConfirmedBooking(user, List.of(4L));

        PaymentRequest paymentRequest = new PaymentRequest(null, true);

        // Initial payment attempt
        MvcResult firstResult = mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        long firstPaymentId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asLong();

        // Repeated payment attempt for the same booking
        MvcResult secondResult = mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        long secondPaymentId = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("id").asLong();

        // Idempotency guarantee: same payment record ID returned
        assertThat(secondPaymentId).isEqualTo(firstPaymentId);

        // Exactly one payment record exists in the database
        List<Payment> payments = paymentRepository.findByBookingId(booking.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getId()).isEqualTo(firstPaymentId);
    }

    // =========================================================================
    // 4. CONCURRENT PAYMENT ATTEMPTS
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("4. Concurrent payment requests cannot create duplicate successful payments")
    public void testConcurrentPaymentsDoNotCreateDuplicateRecords() throws Exception {
        User user = createTestUser("pay_concurrency_user");
        String userId = String.valueOf(user.getId());

        Booking booking = createConfirmedBooking(user, List.of(5L));
        Long bookingId = booking.getId();

        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        ConcurrentLinkedQueue<Payment> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    Payment payment = paymentService.processPayment(bookingId, userId, true);
                    results.add(payment);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(results).hasSize(numberOfThreads);

        // All threads must have resolved to the same payment ID
        Long expectedPaymentId = results.peek().getId();
        assertThat(expectedPaymentId).isNotNull();
        for (Payment payment : results) {
            assertThat(payment.getId()).isEqualTo(expectedPaymentId);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        // Database must contain strictly 1 successful payment record
        List<Payment> successfulPayments = paymentRepository.findByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESS);
        assertThat(successfulPayments).hasSize(1);
    }

    // =========================================================================
    // 5. PAYMENT FAILURES & RE-ATTEMPT BEHAVIOR
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("5. Failed payment records FAILED status and allows subsequent successful payment retry")
    public void testFailedPaymentAndSubsequentRetry() throws Exception {
        User user = createTestUser("pay_failed_retry_user");
        String token = jwtService.generateToken(user);

        Booking booking = createConfirmedBooking(user, List.of(6L));

        // 1. Submit failed payment simulation
        PaymentRequest failRequest = new PaymentRequest(null, false);
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.simulated").value(true));

        // Verify FAILED record in database
        List<Payment> initialPayments = paymentRepository.findByBookingId(booking.getId());
        assertThat(initialPayments).hasSize(1);
        assertThat(initialPayments.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);

        // 2. Retry payment with simulateSuccess = true
        PaymentRequest successRequest = new PaymentRequest(null, true);
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(successRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 3. Payment history now contains 2 records: 1 FAILED, 1 SUCCESS
        List<Payment> finalPayments = paymentRepository.findByBookingId(booking.getId());
        assertThat(finalPayments).hasSize(2);

        List<PaymentStatus> statuses = finalPayments.stream().map(Payment::getStatus).toList();
        assertThat(statuses).containsExactlyInAnyOrder(PaymentStatus.FAILED, PaymentStatus.SUCCESS);
    }

    // =========================================================================
    // 6. CROSS-USER OWNERSHIP ENFORCEMENT
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("6. A booking cannot be paid for or inspected by another user (403 Forbidden)")
    public void testCrossUserPaymentForbidden() throws Exception {
        User userA = createTestUser("pay_owner_a");
        User userB = createTestUser("pay_attacker_b");

        String tokenB = jwtService.generateToken(userB);

        Booking bookingA = createConfirmedBooking(userA, List.of(7L));

        // User B attempts to pay for User A's booking
        PaymentRequest paymentRequest = new PaymentRequest(null, true);
        mockMvc.perform(post("/api/bookings/" + bookingA.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(containsString("does not own booking")));

        // Zero payments exist for Booking A
        assertThat(paymentRepository.findByBookingId(bookingA.getId())).isEmpty();

        // User B attempts to view payments for User A's booking
        mockMvc.perform(get("/api/bookings/" + bookingA.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================================
    // 7. PAYMENT HISTORY ISOLATION
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("7. Payment history returns only the appropriate booking's payment records")
    public void testPaymentHistoryReturnsOnlyTargetBookingPayments() throws Exception {
        User user = createTestUser("pay_history_user");
        String token = jwtService.generateToken(user);

        Booking booking1 = createConfirmedBooking(user, List.of(8L));
        Booking booking2 = createConfirmedBooking(user, List.of(9L));

        paymentService.processPayment(booking1.getId(), String.valueOf(user.getId()), true);
        paymentService.processPayment(booking2.getId(), String.valueOf(user.getId()), true);

        // Query payment history for Booking 1
        MvcResult historyResult1 = mockMvc.perform(get("/api/bookings/" + booking1.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(booking1.getId()))
                .andReturn();

        JsonNode history1 = objectMapper.readTree(historyResult1.getResponse().getContentAsString());
        assertThat(history1.get(0).get("bookingId").asLong()).isEqualTo(booking1.getId());

        // Query payment history for Booking 2
        MvcResult historyResult2 = mockMvc.perform(get("/api/bookings/" + booking2.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(booking2.getId()))
                .andReturn();

        JsonNode history2 = objectMapper.readTree(historyResult2.getResponse().getContentAsString());
        assertThat(history2.get(0).get("bookingId").asLong()).isEqualTo(booking2.getId());
    }

    // =========================================================================
    // 8. INVALID INPUTS & ERROR HANDLING
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("8. Invalid request bodies, unknown IDs, and parameter mismatches produce 400 or 404")
    public void testInvalidRequestsProduceAppropriateClientErrors() throws Exception {
        User user = createTestUser("pay_invalid_input_user");
        String token = jwtService.generateToken(user);

        Booking booking = createConfirmedBooking(user, List.of(10L));

        // Malformed JSON syntax in payment request
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulateSuccess\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Malformed request body or invalid JSON format")));

        // Invalid non-numeric ID in path
        mockMvc.perform(post("/api/bookings/not-a-number/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulateSuccess\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Unknown booking ID on payment history query
        mockMvc.perform(get("/api/bookings/999999/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // 9. DATABASE CONSTRAINTS & ROLLBACK BEHAVIOR
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("9. Database partial unique constraint idx_unique_successful_payment_booking prevents duplicate SUCCESS rows")
    public void testDatabaseConstraintEnforcement() {
        User user = createTestUser("db_constraint_user");
        Booking booking = createConfirmedBooking(user, List.of(11L));

        // First successful payment persists cleanly
        Payment payment1 = new Payment(booking, PaymentStatus.SUCCESS, true);
        paymentRepository.saveAndFlush(payment1);

        // Attempting to directly insert a second SUCCESS payment for the same booking must violate idx_unique_successful_payment_booking
        Payment payment2 = new Payment(booking, PaymentStatus.SUCCESS, true);
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_unique_successful_payment_booking");

        // However, multiple FAILED payments for the same booking ARE permitted by the partial index
        Payment failedPayment1 = new Payment(booking, PaymentStatus.FAILED, true);
        paymentRepository.saveAndFlush(failedPayment1);

        Payment failedPayment2 = new Payment(booking, PaymentStatus.FAILED, true);
        Payment savedFailed2 = paymentRepository.saveAndFlush(failedPayment2);
        assertThat(savedFailed2.getId()).isNotNull();
    }

    // =========================================================================
    // 10. POST-PAYMENT CANCELLATION & SEAT INTEGRITY
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("10. Paid booking can be cancelled, releasing seats while preserving payment history")
    public void testPaidBookingCanBeCancelledAndReleasesSeats() throws Exception {
        User user = createTestUser("pay_cancel_user");
        String token = jwtService.generateToken(user);

        Booking booking = createConfirmedBooking(user, List.of(12L));

        // 1. Process payment
        paymentService.processPayment(booking.getId(), String.valueOf(user.getId()), true);

        // 2. Cancel the paid booking
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 3. Verify seat 12 is now AVAILABLE again in the seat map
        MvcResult seatsResult = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seatsList = objectMapper.readTree(seatsResult.getResponse().getContentAsString());
        for (JsonNode s : seatsList) {
            if (s.get("id").asLong() == 12L) {
                assertThat(s.get("status").asText()).isEqualTo("AVAILABLE");
            }
        }

        // 4. Verify payment history is still preserved for the cancelled booking
        mockMvc.perform(get("/api/bookings/" + booking.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));

        // 5. Subsequent payment attempt on the cancelled booking is rejected
        PaymentRequest retryPayment = new PaymentRequest(null, true);
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retryPayment)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Cannot process payment for a cancelled booking")));
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private User createTestUser(String username) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode("password123"));
        return userRepository.save(user);
    }

    private Booking createConfirmedBooking(User user, List<Long> seatIds) {
        HoldResult hold = holdService.createHold(SHOWTIME_ID, seatIds, String.valueOf(user.getId()));
        return bookingService.confirmBooking(
                SHOWTIME_ID,
                seatIds,
                hold.holdToken(),
                String.valueOf(user.getId()),
                "idemp-pay-" + UUID.randomUUID()
        );
    }
}
