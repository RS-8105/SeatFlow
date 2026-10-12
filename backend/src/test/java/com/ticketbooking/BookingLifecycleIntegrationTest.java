package com.ticketbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingSeat;
import com.ticketbooking.domain.BookingStatus;
import com.ticketbooking.domain.Payment;
import com.ticketbooking.domain.PaymentStatus;
import com.ticketbooking.domain.User;
import com.ticketbooking.dto.ConfirmBookingRequest;
import com.ticketbooking.dto.CreateHoldRequest;
import com.ticketbooking.dto.PaymentRequest;
import com.ticketbooking.dto.ReleaseHoldRequest;
import com.ticketbooking.dto.SeatStateEvent;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.PaymentRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowtimeRepository;
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
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TicketBookingApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BookingLifecycleIntegrationTest {

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
    private SeatRepository seatRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private com.ticketbooking.service.RedisLockService redisLockService;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private ChannelTopic seatEventsTopic;

    private static final Long SHOWTIME_ID_1 = 1L;
    private static final Long SHOWTIME_ID_2 = 2L;

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
    // 1. EVENT & SHOWTIME DISCOVERY
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Retrieve events and event details")
    public void testRetrieveEventsAndEventDetails() throws Exception {
        // Retrieve all events
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].name").value("Coldplay: Music of the Spheres"))
                .andExpect(jsonPath("$[1].name").value("Hans Zimmer Live in Concert"));

        // Retrieve event by ID
        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Coldplay: Music of the Spheres"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/events/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Hans Zimmer Live in Concert"));
    }

    @Test
    @Order(2)
    @DisplayName("2. Retrieve showtimes for an event and seats for a showtime")
    public void testRetrieveShowtimesAndSeats() throws Exception {
        // Retrieve showtimes for Event 1
        mockMvc.perform(get("/api/events/1/showtimes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(SHOWTIME_ID_1))
                .andExpect(jsonPath("$[0].eventId").value(1))
                .andExpect(jsonPath("$[0].venueName").value("Starlight Amphitheater"))
                .andExpect(jsonPath("$[0].venueCity").value("San Francisco"))
                .andExpect(jsonPath("$[0].startsAt").isNotEmpty());

        // Retrieve seat inventory for Showtime 1
        MvcResult seatsResult = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID_1 + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(60))
                .andReturn();

        JsonNode seatsJson = objectMapper.readTree(seatsResult.getResponse().getContentAsString());
        // Verify VIP seats (Rows A and B) and STANDARD seats (Rows C-F)
        JsonNode firstSeat = seatsJson.get(0);
        assertThat(firstSeat.get("rowLabel").asText()).isEqualTo("A");
        assertThat(firstSeat.get("category").asText()).isEqualTo("VIP");
        assertThat(firstSeat.get("price").asDouble()).isEqualTo(150.00);
        assertThat(firstSeat.get("status").asText()).isEqualTo("AVAILABLE");

        // Verify standard seat
        boolean foundStandard = false;
        for (JsonNode seatNode : seatsJson) {
            if ("STANDARD".equals(seatNode.get("category").asText())) {
                assertThat(seatNode.get("price").asDouble()).isEqualTo(75.00);
                assertThat(seatNode.get("status").asText()).isEqualTo("AVAILABLE");
                foundStandard = true;
                break;
            }
        }
        assertThat(foundStandard).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("3. Handle unknown event, showtime, and seat IDs correctly")
    public void testHandleUnknownIds() throws Exception {
        // Unknown event ID
        mockMvc.perform(get("/api/events/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Event not found with ID: 999999"));

        // Unknown event showtimes
        mockMvc.perform(get("/api/events/999999/showtimes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Unknown showtime seats
        mockMvc.perform(get("/api/showtimes/999999/seats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Unknown showtime hold attempt (authenticated)
        User user = createTestUser("unknown_id_user");
        String token = jwtService.generateToken(user);

        mockMvc.perform(post("/api/showtimes/999999/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(1L), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Showtime not found with ID: 999999"));

        // Unknown seat ID hold attempt
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(999999L), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("One or more requested seats do not exist"));
    }

    // =========================================================================
    // 2. SEAT HOLDING & VALIDATION
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("4. Create a valid seat hold and verify returned hold details, expiry, and inventory status")
    public void testCreateValidSeatHold() throws Exception {
        User user = createTestUser("hold_tester");
        String token = jwtService.generateToken(user);

        CreateHoldRequest holdReq = new CreateHoldRequest(List.of(1L, 2L), null);

        MvcResult result = mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(holdReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdToken").isString())
                .andExpect(jsonPath("$.showtimeId").value(SHOWTIME_ID_1))
                .andExpect(jsonPath("$.userId").value(String.valueOf(user.getId())))
                .andExpect(jsonPath("$.seatIds").isArray())
                .andExpect(jsonPath("$.seatIds.length()").value(2))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String holdToken = json.get("holdToken").asText();
        Instant expiresAt = Instant.parse(json.get("expiresAt").asText());
        assertThat(expiresAt).isAfter(Instant.now());

        // Verify Redis hold keys exist in isolated Redis DB 1
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID_1, 1L)).isTrue();
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID_1, 2L)).isTrue();
        assertThat(redisLockService.getHoldToken(SHOWTIME_ID_1, 1L)).isEqualTo(holdToken);
        assertThat(redisLockService.getHoldToken(SHOWTIME_ID_1, 2L)).isEqualTo(holdToken);
        assertThat(redisTemplate.opsForValue().get(redisLockService.buildHoldOwnerKey(holdToken))).isEqualTo(String.valueOf(user.getId()));

        // Verify seats report status "HELD" on GET /api/showtimes/{id}/seats
        MvcResult seatsResult = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID_1 + "/seats"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode seatsJson = objectMapper.readTree(seatsResult.getResponse().getContentAsString());
        for (JsonNode seatNode : seatsJson) {
            long seatId = seatNode.get("id").asLong();
            if (seatId == 1L || seatId == 2L) {
                assertThat(seatNode.get("status").asText()).isEqualTo("HELD");
            } else {
                assertThat(seatNode.get("status").asText()).isEqualTo("AVAILABLE");
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. Reject holds for already-held, already-booked, and invalid seats")
    public void testRejectHoldsForAlreadyHeldAndBookedSeats() throws Exception {
        User userA = createTestUser("user_hold_a");
        User userB = createTestUser("user_hold_b");

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        // 1. User A holds seat 3
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(3L), null))))
                .andExpect(status().isCreated());

        // 2. User B tries to hold already-held seat 3 -> 409 Conflict
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(3L), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("One or more requested seats are currently held by another user"));

        // 3. User A confirms booking for seat 3
        HoldResult holdA = holdService.createHold(SHOWTIME_ID_1, List.of(4L), String.valueOf(userA.getId()));
        bookingService.confirmBooking(
                SHOWTIME_ID_1,
                List.of(4L),
                holdA.holdToken(),
                String.valueOf(userA.getId()),
                "idemp-booked-seat-4"
        );

        // 4. User B attempts to hold already-booked seat 4 -> 409 Conflict
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(4L), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Seats already confirmed for this showtime")));
    }

    @Test
    @Order(6)
    @DisplayName("6. Reject empty, duplicate, or excessive seat selections")
    public void testRejectInvalidSeatSelections() throws Exception {
        User user = createTestUser("invalid_sel_user");
        String token = jwtService.generateToken(user);

        // Empty seat selection
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Duplicate seat ID inside request
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(5L, 5L), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Duplicate seat ID detected in request: 5"));

        // Exceeding maximum seats per user limit (limit is 6 in application-test.yml)
        List<Long> sevenSeats = List.of(5L, 6L, 7L, 8L, 9L, 10L, 11L);
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(sevenSeats, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("exceeds maximum limit")));
    }

    // =========================================================================
    // 3. BOOKING CONFIRMATION & IDEMPOTENCY
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("7. Confirm a valid hold and verify resulting booking and booking-seat records in PostgreSQL")
    public void testConfirmValidHoldAndVerifyDatabaseRecords() throws Exception {
        User user = createTestUser("confirm_user");
        String token = jwtService.generateToken(user);

        // 1. Create hold on seat 12 (VIP, $150.00) and seat 25 (STANDARD, $75.00)
        HoldResult hold = holdService.createHold(SHOWTIME_ID_1, List.of(12L, 25L), String.valueOf(user.getId()));

        ConfirmBookingRequest confirmReq = new ConfirmBookingRequest(
                SHOWTIME_ID_1,
                List.of(12L, 25L),
                hold.holdToken(),
                null
        );

        String idempotencyKey = "bkg-idemp-001-" + UUID.randomUUID();

        // 2. Confirm booking via POST /api/holds/confirm
        MvcResult confirmResult = mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(String.valueOf(user.getId())))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(225.00))
                .andExpect(jsonPath("$.eventName").value("Coldplay: Music of the Spheres"))
                .andExpect(jsonPath("$.venueName").value("Starlight Amphitheater"))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andReturn();

        JsonNode bookingJson = objectMapper.readTree(confirmResult.getResponse().getContentAsString());
        long bookingId = bookingJson.get("id").asLong();

        // 3. Verify PostgreSQL Booking persistence
        Booking savedBooking = bookingRepository.findById(bookingId).orElse(null);
        assertThat(savedBooking).isNotNull();
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(savedBooking.getUserId()).isEqualTo(String.valueOf(user.getId()));
        assertThat(savedBooking.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(225.00));
        assertThat(savedBooking.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // 4. Verify PostgreSQL BookingSeat persistence
        List<BookingSeat> savedSeats = bookingSeatRepository.findByBookingId(bookingId);
        assertThat(savedSeats).hasSize(2);
        for (BookingSeat bs : savedSeats) {
            assertThat(bs.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(bs.getShowtime().getId()).isEqualTo(SHOWTIME_ID_1);
        }

        // 5. Verify Redis hold locks were cleaned up post-commit
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID_1, 12L)).isFalse();
        assertThat(redisLockService.isSeatHeld(SHOWTIME_ID_1, 25L)).isFalse();

        // 6. Verify seat status in inventory is now BOOKED
        MvcResult seatsResult = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID_1 + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seatsList = objectMapper.readTree(seatsResult.getResponse().getContentAsString());
        for (JsonNode s : seatsList) {
            long sId = s.get("id").asLong();
            if (sId == 12L || sId == 25L) {
                assertThat(s.get("status").asText()).isEqualTo("BOOKED");
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. Reject confirmation with missing, expired, released, or other user's hold")
    public void testRejectConfirmationWithInvalidOrOtherUserHold() throws Exception {
        User userA = createTestUser("conf_user_a");
        User userB = createTestUser("conf_user_b");

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        // 1. Missing / Non-existent hold token
        ConfirmBookingRequest missingHoldReq = new ConfirmBookingRequest(
                SHOWTIME_ID_1,
                List.of(15L),
                "completely-bogus-token",
                null
        );
        mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .header("Idempotency-Key", "key-missing-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingHoldReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Hold has expired or does not belong to user")));

        // 2. Released hold
        HoldResult holdA = holdService.createHold(SHOWTIME_ID_1, List.of(16L), String.valueOf(userA.getId()));
        holdService.releaseHold(SHOWTIME_ID_1, List.of(16L), holdA.holdToken(), String.valueOf(userA.getId()));

        ConfirmBookingRequest releasedHoldReq = new ConfirmBookingRequest(
                SHOWTIME_ID_1,
                List.of(16L),
                holdA.holdToken(),
                null
        );
        mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .header("Idempotency-Key", "key-released-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releasedHoldReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 3. Other user's hold (User B tries to confirm User A's hold)
        HoldResult activeHoldA = holdService.createHold(SHOWTIME_ID_1, List.of(17L), String.valueOf(userA.getId()));
        ConfirmBookingRequest otherUserHoldReq = new ConfirmBookingRequest(
                SHOWTIME_ID_1,
                List.of(17L),
                activeHoldA.holdToken(),
                null
        );
        mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .header("Idempotency-Key", "key-other-user-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherUserHoldReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @Order(9)
    @DisplayName("9. Verify idempotent confirmation behavior and conflict handling for reused keys with different requests")
    public void testIdempotentConfirmationAndConflictHandling() throws Exception {
        User user = createTestUser("idemp_conflict_user");
        String token = jwtService.generateToken(user);

        HoldResult hold = holdService.createHold(SHOWTIME_ID_1, List.of(18L), String.valueOf(user.getId()));
        String idempotencyKey = "shared-idempotency-key-" + UUID.randomUUID();

        ConfirmBookingRequest originalReq = new ConfirmBookingRequest(
                SHOWTIME_ID_1,
                List.of(18L),
                hold.holdToken(),
                null
        );

        // First confirmation -> 201 Created
        MvcResult firstResult = mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long originalBookingId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asLong();

        // Exact replay with same idempotency key -> 201 Created, same booking ID
        MvcResult replayResult = mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long replayBookingId = objectMapper.readTree(replayResult.getResponse().getContentAsString()).get("id").asLong();
        assertThat(replayBookingId).isEqualTo(originalBookingId);

        // Reusing same idempotency key with differing seat selection -> 409 Conflict
        ConfirmBookingRequest differingSeatsReq = new ConfirmBookingRequest(
                SHOWTIME_ID_1,
                List.of(19L),
                "another-token",
                null
        );
        mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(differingSeatsReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Idempotency key reused with differing seat selection")));
    }

    // =========================================================================
    // 4. BOOKING RETRIEVAL & PAYMENT HISTORY
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("10. Retrieve user's booking list, booking details, and payment history")
    public void testRetrieveBookingListDetailsAndPayments() throws Exception {
        User user = createTestUser("history_user");
        String token = jwtService.generateToken(user);

        // 1. Confirm a booking
        HoldResult hold = holdService.createHold(SHOWTIME_ID_1, List.of(20L), String.valueOf(user.getId()));
        Booking booking = bookingService.confirmBooking(
                SHOWTIME_ID_1,
                List.of(20L),
                hold.holdToken(),
                String.valueOf(user.getId()),
                "idemp-history-001"
        );

        // 2. GET /api/bookings/me -> should contain the booking
        mockMvc.perform(get("/api/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(booking.getId()))
                .andExpect(jsonPath("$[0].userId").value(String.valueOf(user.getId())))
                .andExpect(jsonPath("$[0].eventName").value("Coldplay: Music of the Spheres"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        // 3. GET /api/bookings/{id} -> returns booking details
        mockMvc.perform(get("/api/bookings/" + booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.totalAmount").value(150.00))
                .andExpect(jsonPath("$.seats[0].seatId").value(20));

        // 4. Process payment POST /api/bookings/{id}/payment
        PaymentRequest paymentRequest = new PaymentRequest(null, true);
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.bookingId").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.simulated").value(true));

        // 5. GET /api/bookings/{id}/payments -> returns payment record
        mockMvc.perform(get("/api/bookings/" + booking.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(booking.getId()))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    // =========================================================================
    // 5. CANCELLATION & SEAT RELEASE
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("11. Cancel a booking and verify its status and seat availability follow domain rules")
    public void testCancelBookingAndVerifySeatAvailability() throws Exception {
        User user = createTestUser("cancellation_user");
        String token = jwtService.generateToken(user);

        // 1. Confirm a booking on seat 21
        HoldResult hold = holdService.createHold(SHOWTIME_ID_1, List.of(21L), String.valueOf(user.getId()));
        Booking booking = bookingService.confirmBooking(
                SHOWTIME_ID_1,
                List.of(21L),
                hold.holdToken(),
                String.valueOf(user.getId()),
                "idemp-cancel-001"
        );

        // Verify seat 21 is BOOKED
        MvcResult seatsBefore = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID_1 + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(findSeatStatus(seatsBefore, 21L)).isEqualTo("BOOKED");

        // 2. Cancel booking via POST /api/bookings/{id}/cancel
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 3. Verify PostgreSQL status transitions
        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElse(null);
        assertThat(updatedBooking).isNotNull();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
        for (BookingSeat bs : bookingSeats) {
            assertThat(bs.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        // 4. Verify seat 21 is now AVAILABLE again in the public seat map
        MvcResult seatsAfter = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID_1 + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(findSeatStatus(seatsAfter, 21L)).isEqualTo("AVAILABLE");

        // 5. Verify that another user can now hold and book seat 21
        User anotherUser = createTestUser("new_occupant");
        String anotherToken = jwtService.generateToken(anotherUser);

        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + anotherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(21L), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatIds[0]").value(21L));
    }

    @Test
    @Order(12)
    @DisplayName("12. Verify repeated cancellation and invalid status transitions are handled consistently")
    public void testRepeatedCancellationAndInvalidStatusTransitions() throws Exception {
        User user = createTestUser("invalid_transition_user");
        String token = jwtService.generateToken(user);

        // Create and cancel booking on seat 22
        HoldResult hold = holdService.createHold(SHOWTIME_ID_1, List.of(22L), String.valueOf(user.getId()));
        Booking booking = bookingService.confirmBooking(
                SHOWTIME_ID_1,
                List.of(22L),
                hold.holdToken(),
                String.valueOf(user.getId()),
                "idemp-invalid-trans-001"
        );
        bookingService.cancelBooking(booking.getId(), String.valueOf(user.getId()));

        // Repeated cancellation returns cancelled booking idempotently (200 OK)
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Attempting payment on a cancelled booking -> 400 Bad Request
        PaymentRequest paymentRequest = new PaymentRequest(null, true);
        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Cannot process payment for a cancelled booking")));
    }

    // =========================================================================
    // 6. SHOWTIME ISOLATION & SSE BROADCAST
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("13. Verify booking and hold operations do not corrupt unrelated showtimes or seats")
    public void testShowtimeAndSeatIsolation() throws Exception {
        User user = createTestUser("isolation_user");
        String token = jwtService.generateToken(user);

        // Hold seat 30 on Showtime 1
        HoldResult hold1 = holdService.createHold(SHOWTIME_ID_1, List.of(30L), String.valueOf(user.getId()));

        // Confirm booking for seat 30 on Showtime 1
        bookingService.confirmBooking(
                SHOWTIME_ID_1,
                List.of(30L),
                hold1.holdToken(),
                String.valueOf(user.getId()),
                "idemp-isolation-001"
        );

        // 1. In Showtime 1, seat 30 is BOOKED
        MvcResult seatsSt1 = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID_1 + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(findSeatStatus(seatsSt1, 30L)).isEqualTo("BOOKED");
        // Seat 31 in Showtime 1 remains AVAILABLE
        assertThat(findSeatStatus(seatsSt1, 31L)).isEqualTo("AVAILABLE");

        // 2. In Showtime 2 (different showtime sharing Venue 1), seat 30 remains AVAILABLE!
        MvcResult seatsSt2 = mockMvc.perform(get("/api/showtimes/" + SHOWTIME_ID_2 + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(findSeatStatus(seatsSt2, 30L)).isEqualTo("AVAILABLE");

        // 3. User can hold seat 30 in Showtime 2 without conflict
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_2 + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(30L), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatIds[0]").value(30L))
                .andExpect(jsonPath("$.showtimeId").value(SHOWTIME_ID_2));
    }

    @Test
    @Order(14)
    @DisplayName("14. Verify SSE seat-state changes via Redis Pub/Sub events on hold and release")
    public void testSseSeatStateEvents() throws Exception {
        User user = createTestUser("sse_listener_user");
        String token = jwtService.generateToken(user);

        List<SeatStateEvent> receivedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        MessageListener testListener = new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                try {
                    String json = new String(message.getBody());
                    SeatStateEvent event = objectMapper.readValue(json, SeatStateEvent.class);
                    if (Objects.equals(event.showtimeId(), SHOWTIME_ID_1) && Objects.equals(event.seatId(), 35L)) {
                        receivedEvents.add(event);
                        latch.countDown();
                    }
                } catch (Exception ignored) {
                }
            }
        };

        redisMessageListenerContainer.addMessageListener(testListener, seatEventsTopic);

        try {
            // 1. Create hold on seat 35 -> emits HELD event
            MvcResult holdResult = mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID_1 + "/holds")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateHoldRequest(List.of(35L), null))))
                    .andExpect(status().isCreated())
                    .andReturn();

            String holdToken = objectMapper.readTree(holdResult.getResponse().getContentAsString()).get("holdToken").asText();

            // 2. Release hold on seat 35 -> emits AVAILABLE event
            mockMvc.perform(delete("/api/holds")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReleaseHoldRequest(SHOWTIME_ID_1, List.of(35L), holdToken, null))))
                    .andExpect(status().isOk());

            // Await Redis Pub/Sub event delivery (timeout 5s)
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            assertThat(receivedEvents).hasSize(2);
            assertThat(receivedEvents.get(0).status()).isEqualTo("HELD");
            assertThat(receivedEvents.get(0).seatId()).isEqualTo(35L);
            assertThat(receivedEvents.get(1).status()).isEqualTo("AVAILABLE");
            assertThat(receivedEvents.get(1).seatId()).isEqualTo(35L);
        } finally {
            redisMessageListenerContainer.removeMessageListener(testListener, seatEventsTopic);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private User createTestUser(String username) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode("password123"));
        return userRepository.save(user);
    }

    private String findSeatStatus(MvcResult result, long targetSeatId) throws Exception {
        JsonNode seats = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode s : seats) {
            if (s.get("id").asLong() == targetSeatId) {
                return s.get("status").asText();
            }
        }
        return null;
    }
}
