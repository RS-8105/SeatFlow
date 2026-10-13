package com.ticketbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.User;
import com.ticketbooking.dto.ConfirmBookingRequest;
import com.ticketbooking.dto.CreateHoldRequest;
import com.ticketbooking.dto.LoginRequest;
import com.ticketbooking.dto.PaymentRequest;
import com.ticketbooking.dto.RegisterRequest;
import com.ticketbooking.dto.ReleaseHoldRequest;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.PaymentRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.security.JwtService;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.HoldResult;
import com.ticketbooking.service.HoldService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TicketBookingApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiSecurityIntegrationTest {

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
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${ticketbooking.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String jwtSecret;

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
    // 1. REGISTRATION TESTS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Registration succeeds with valid data and creates hashed user")
    public void registration_succeedsWithValidData() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.expiresInMs").isNumber())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("password");
        assertThat(responseBody).doesNotContain("passwordHash");

        // Verify user persistence and BCrypt hashing
        User savedUser = userRepository.findByUsername("alice").orElse(null);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("2a. Registration rejects duplicate username with 409 Conflict")
    public void registration_rejectsDuplicateUsername() throws Exception {
        // First registration
        RegisterRequest first = new RegisterRequest("bob_dup", "bob1@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Attempt second registration with same username
        RegisterRequest duplicateUsername = new RegisterRequest("bob_dup", "bob2@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateUsername)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Username 'bob_dup' is already taken"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("2b. Registration rejects duplicate email with 409 Conflict")
    public void registration_rejectsDuplicateEmail() throws Exception {
        // First registration
        RegisterRequest first = new RegisterRequest("user_one", "shared@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Attempt second registration with same email
        RegisterRequest duplicateEmail = new RegisterRequest("user_two", "shared@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email 'shared@example.com' is already registered"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    @Order(4)
    @DisplayName("2c. Registration rejects invalid input (blank, short username, bad email, short password)")
    public void registration_rejectsInvalidInput() throws Exception {
        // Blank username
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("", "valid@example.com", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));

        // Short username (< 3 chars)
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("ab", "valid@example.com", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Invalid email format
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("validuser", "not-an-email", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Short password (< 6 chars)
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("validuser", "valid@example.com", "12345"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // 2. LOGIN TESTS
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("3a. Login succeeds with valid username credentials")
    public void login_succeedsWithValidUsernameCredentials() throws Exception {
        registerTestUser("dave", "dave@example.com", "mySecretPass!");

        LoginRequest request = new LoginRequest("dave", "mySecretPass!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("dave"))
                .andExpect(jsonPath("$.email").value("dave@example.com"))
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.expiresInMs").isNumber());
    }

    @Test
    @Order(6)
    @DisplayName("3b. Login succeeds with valid email (case-insensitive)")
    public void login_succeedsWithValidEmailCredentials() throws Exception {
        registerTestUser("emily", "emily@example.com", "secure123");

        // Login using mixed-case email
        LoginRequest request = new LoginRequest("Emily@Example.COM", "secure123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("emily"))
                .andExpect(jsonPath("$.email").value("emily@example.com"));
    }

    @Test
    @Order(7)
    @DisplayName("3c. Login rejects incorrect password with 401 Unauthorized")
    public void login_rejectsIncorrectPassword() throws Exception {
        registerTestUser("frank", "frank@example.com", "correctPass1");

        LoginRequest request = new LoginRequest("frank", "wrongPass");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    @Order(8)
    @DisplayName("3d. Login rejects non-existent user with 401 Unauthorized")
    public void login_rejectsNonExistentUser() throws Exception {
        LoginRequest request = new LoginRequest("ghost_user", "somePassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @Order(9)
    @DisplayName("3e. Login rejects blank credentials with 400 Bad Request")
    public void login_rejectsBlankCredentials() throws Exception {
        LoginRequest request = new LoginRequest("", "");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // 3. JWT TOKEN VALIDATION & PROTECTED ENDPOINTS
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("4a. Protected endpoints reject missing Authorization header")
    public void protectedEndpoint_rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/bookings/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/bookings/me"));
    }

    @Test
    @Order(11)
    @DisplayName("4b. Protected endpoints reject malformed JWT token")
    public void protectedEndpoint_rejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.structure"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Order(12)
    @DisplayName("4c. Protected endpoints reject expired JWT token")
    public void protectedEndpoint_rejectsExpiredToken() throws Exception {
        User user = registerTestUser("george", "george@example.com", "pass123");
        String expiredToken = createExpiredToken(user.getUsername(), user.getId());

        mockMvc.perform(get("/api/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Order(13)
    @DisplayName("4d. Protected endpoints reject token signed with untrusted secret key")
    public void protectedEndpoint_rejectsUntrustedSignatureToken() throws Exception {
        User user = registerTestUser("helen", "helen@example.com", "pass123");
        String untrustedToken = createUntrustedToken(user.getUsername(), user.getId());

        mockMvc.perform(get("/api/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + untrustedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Order(14)
    @DisplayName("4e. Protected endpoints reject non-Bearer authorization scheme")
    public void protectedEndpoint_rejectsNonBearerScheme() throws Exception {
        mockMvc.perform(get("/api/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Order(15)
    @DisplayName("5. Authenticated users can access permitted protected endpoints")
    public void authenticatedUser_canAccessPermittedEndpoints() throws Exception {
        User user = registerTestUser("ian", "ian@example.com", "pass123");
        String token = jwtService.generateToken(user);

        // 1. Can access GET /api/bookings/me
        mockMvc.perform(get("/api/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 2. Can create a hold via POST /api/showtimes/{id}/holds
        CreateHoldRequest holdRequest = new CreateHoldRequest(List.of(1L, 2L), null);
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(holdRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdToken").isString())
                .andExpect(jsonPath("$.showtimeId").value(SHOWTIME_ID))
                .andExpect(jsonPath("$.userId").value(String.valueOf(user.getId())))
                .andExpect(jsonPath("$.seatIds").isArray());
    }

    // =========================================================================
    // 4. OWNERSHIP & CROSS-USER AUTHORIZATION
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("6a. Users cannot access another user's booking details (403 Forbidden)")
    public void ownership_userCannotAccessOtherUserBooking() throws Exception {
        User userA = registerTestUser("user_a", "usera@example.com", "pass123");
        User userB = registerTestUser("user_b", "userb@example.com", "pass123");

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        // User A creates a hold and confirms a booking
        HoldResult holdA = holdService.createHold(SHOWTIME_ID, List.of(3L), String.valueOf(userA.getId()));
        Booking bookingA = bookingService.confirmBooking(
                SHOWTIME_ID,
                List.of(3L),
                holdA.holdToken(),
                String.valueOf(userA.getId()),
                "idemp-key-user-a-1"
        );

        // User B attempts to access User A's booking
        mockMvc.perform(get("/api/bookings/" + bookingA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/bookings/" + bookingA.getId()));

        // User A CAN access their own booking
        mockMvc.perform(get("/api/bookings/" + bookingA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingA.getId()))
                .andExpect(jsonPath("$.userId").value(String.valueOf(userA.getId())));
    }

    @Test
    @Order(17)
    @DisplayName("6b. Users cannot cancel another user's booking (403 Forbidden)")
    public void ownership_userCannotCancelOtherUserBooking() throws Exception {
        User userA = registerTestUser("user_cancel_a", "cancel_a@example.com", "pass123");
        User userB = registerTestUser("user_cancel_b", "cancel_b@example.com", "pass123");

        String tokenB = jwtService.generateToken(userB);

        HoldResult holdA = holdService.createHold(SHOWTIME_ID, List.of(4L), String.valueOf(userA.getId()));
        Booking bookingA = bookingService.confirmBooking(
                SHOWTIME_ID,
                List.of(4L),
                holdA.holdToken(),
                String.valueOf(userA.getId()),
                "idemp-key-cancel-1"
        );

        // User B attempts to cancel User A's booking
        mockMvc.perform(post("/api/bookings/" + bookingA.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @Order(18)
    @DisplayName("6c. Users cannot process payment or view payments for another user's booking (403 Forbidden)")
    public void ownership_userCannotPayOrViewPaymentsForOtherUserBooking() throws Exception {
        User userA = registerTestUser("user_pay_a", "pay_a@example.com", "pass123");
        User userB = registerTestUser("user_pay_b", "pay_b@example.com", "pass123");

        String tokenB = jwtService.generateToken(userB);

        HoldResult holdA = holdService.createHold(SHOWTIME_ID, List.of(5L), String.valueOf(userA.getId()));
        Booking bookingA = bookingService.confirmBooking(
                SHOWTIME_ID,
                List.of(5L),
                holdA.holdToken(),
                String.valueOf(userA.getId()),
                "idemp-key-pay-1"
        );

        PaymentRequest paymentRequest = new PaymentRequest(null, true);

        // User B attempts payment on User A's booking
        mockMvc.perform(post("/api/bookings/" + bookingA.getId() + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // User B attempts to view payments on User A's booking
        mockMvc.perform(get("/api/bookings/" + bookingA.getId() + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @Order(19)
    @DisplayName("6d. Users cannot release another user's hold (403 Forbidden)")
    public void ownership_userCannotReleaseOtherUserHold() throws Exception {
        User userA = registerTestUser("user_hold_a", "hold_a@example.com", "pass123");
        User userB = registerTestUser("user_hold_b", "hold_b@example.com", "pass123");

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        HoldResult holdA = holdService.createHold(SHOWTIME_ID, List.of(6L), String.valueOf(userA.getId()));

        ReleaseHoldRequest releaseRequest = new ReleaseHoldRequest(
                SHOWTIME_ID,
                List.of(6L),
                holdA.holdToken(),
                null
        );

        // User B attempts to release User A's hold
        mockMvc.perform(delete("/api/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // User A CAN release their own hold
        mockMvc.perform(delete("/api/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyReleased").value(true));
    }

    // =========================================================================
    // 5. PUBLIC ENDPOINTS CONFIGURATION
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("7a. Public event and showtime endpoints behave according to security configuration (unauthenticated GET permitted)")
    public void publicEndpoints_accessibleWithoutAuthentication() throws Exception {
        // GET /api/events
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // GET /api/events/{id}
        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").isNotEmpty());

        // GET /api/events/{id}/showtimes
        mockMvc.perform(get("/api/events/1/showtimes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // GET /api/showtimes/{id}/seats
        mockMvc.perform(get("/api/showtimes/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Actuator health
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(21)
    @DisplayName("7b. Non-GET requests on public endpoints require authentication")
    public void nonGetRequest_onPublicPath_requiresAuthentication() throws Exception {
        // Unauthenticated POST /api/events is rejected
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Unauthorized Event\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // =========================================================================
    // 6. INVALID IDS, MALFORMED BODIES, MISSING HEADERS & VALUES
    // =========================================================================

    @Test
    @Order(22)
    @DisplayName("8a. Non-existent IDs produce 404 Not Found")
    public void nonExistentIds_return404NotFound() throws Exception {
        mockMvc.perform(get("/api/events/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/events/999999"));

        mockMvc.perform(get("/api/showtimes/999999/seats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @Order(23)
    @DisplayName("8b. Invalid ID formats produce 400 Bad Request")
    public void invalidIdFormat_returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/events/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/events/not-a-number"));
    }

    @Test
    @Order(24)
    @DisplayName("8c. Malformed request body produces 400 Bad Request")
    public void malformedRequestBody_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"broken_json\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed request body or invalid JSON format"));
    }

    @Test
    @Order(25)
    @DisplayName("8d. Missing required Idempotency-Key header produces 400 Bad Request")
    public void missingRequiredHeader_returns400BadRequest() throws Exception {
        User user = registerTestUser("header_user", "header@example.com", "pass123");
        String token = jwtService.generateToken(user);

        ConfirmBookingRequest confirmRequest = new ConfirmBookingRequest(
                SHOWTIME_ID,
                List.of(7L),
                "some-token",
                null
        );

        // POST without Idempotency-Key header
        mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is missing"));
    }

    @Test
    @Order(26)
    @DisplayName("8e. Empty seatIds list produces 400 Bad Request")
    public void emptySeatIds_returns400BadRequest() throws Exception {
        User user = registerTestUser("empty_user", "empty@example.com", "pass123");
        String token = jwtService.generateToken(user);

        CreateHoldRequest emptyRequest = new CreateHoldRequest(List.of(), null);
        mockMvc.perform(post("/api/showtimes/" + SHOWTIME_ID + "/holds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // 7. ERROR RESPONSE CONSISTENCY & SENSITIVE DATA LEAK PREVENTION
    // =========================================================================

    @Test
    @Order(27)
    @DisplayName("9. Error responses have consistent status codes and response structures")
    public void errorResponses_haveConsistentStructure() throws Exception {
        // Test 400
        MvcResult res400 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("", "", ""))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertValidErrorStructure(res400, 400, "Bad Request", "/api/auth/register");

        // Test 401
        MvcResult res401 = mockMvc.perform(get("/api/bookings/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertValidErrorStructure(res401, 401, "Unauthorized", "/api/bookings/me");

        // Test 404
        MvcResult res404 = mockMvc.perform(get("/api/events/888888"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertValidErrorStructure(res404, 404, "Not Found", "/api/events/888888");

        // Test 409
        registerTestUser("dup_struct", "dup_struct@example.com", "pass123");
        MvcResult res409 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("dup_struct", "diff@example.com", "pass123"))))
                .andExpect(status().isConflict())
                .andReturn();
        assertValidErrorStructure(res409, 409, "Conflict", "/api/auth/register");
    }

    @Test
    @Order(28)
    @DisplayName("10. Failures do not expose passwords, tokens, stack traces, or sensitive internals")
    public void failures_doNotExposeSensitiveDetails() throws Exception {
        // Bad login attempt
        MvcResult loginFail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "secret_attempt_123"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = loginFail.getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret_attempt_123");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain(jwtSecret);
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("at com.ticketbooking");
        assertThat(body).doesNotContain("org.springframework");

        // Malformed request
        MvcResult malformedFail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bad\": "))
                .andExpect(status().isBadRequest())
                .andReturn();

        String malformedBody = malformedFail.getResponse().getContentAsString();
        assertThat(malformedBody).doesNotContain("JsonParseException");
        assertThat(malformedBody).doesNotContain("com.fasterxml.jackson");
        assertThat(malformedBody).doesNotContain("at ");
    }

    // =========================================================================
    // 8. EDGE CASES: EXPIRED HOLDS & IDEMPOTENCY
    // =========================================================================

    @Test
    @Order(29)
    @DisplayName("11. Confirming booking with expired or non-existent hold returns 404 Not Found")
    public void confirmBooking_withNonExistentHold_returns404() throws Exception {
        User user = registerTestUser("expired_hold_user", "expired_hold@example.com", "pass123");
        String token = jwtService.generateToken(user);

        ConfirmBookingRequest confirmRequest = new ConfirmBookingRequest(
                SHOWTIME_ID,
                List.of(8L),
                "non-existent-hold-token",
                null
        );

        mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "idemp-expired-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @Order(30)
    @DisplayName("12. Idempotent booking replay returns identical booking with 201 Created")
    public void confirmBooking_idempotentReplayReturnsSameBooking() throws Exception {
        User user = registerTestUser("idemp_user", "idemp@example.com", "pass123");
        String token = jwtService.generateToken(user);

        HoldResult hold = holdService.createHold(SHOWTIME_ID, List.of(9L), String.valueOf(user.getId()));
        String idempotencyKey = "idemp-replay-key-001";

        ConfirmBookingRequest confirmRequest = new ConfirmBookingRequest(
                SHOWTIME_ID,
                List.of(9L),
                hold.holdToken(),
                null
        );

        // Initial confirmation
        MvcResult firstResult = mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        long bookingId = firstJson.get("id").asLong();

        // Replay with identical idempotency key
        MvcResult replayResult = mockMvc.perform(post("/api/holds/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andReturn();

        JsonNode replayJson = objectMapper.readTree(replayResult.getResponse().getContentAsString());
        assertThat(replayJson.get("id").asLong()).isEqualTo(bookingId);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private User registerTestUser(String username, String email, String password) {
        User user = new User(username, email.toLowerCase(), passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    private String createExpiredToken(String username, Long userId) {
        Instant now = Instant.now();
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", username + "@example.com")
                .subject(username)
                .issuedAt(Date.from(now.minus(Duration.ofHours(2))))
                .expiration(Date.from(now.minus(Duration.ofHours(1))))
                .signWith(key)
                .compact();
    }

    private String createUntrustedToken(String username, Long userId) {
        Instant now = Instant.now();
        // 32-byte key generated with different material
        byte[] wrongKeyBytes = Decoders.BASE64.decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongKeyBytes);
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", username + "@example.com")
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(wrongKey)
                .compact();
    }

    private void assertValidErrorStructure(MvcResult result, int expectedStatus, String expectedError, String expectedPath) throws Exception {
        String json = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("timestamp")).isTrue();
        assertThat(node.get("timestamp").asText()).isNotEmpty();
        assertThat(Instant.parse(node.get("timestamp").asText())).isNotNull();

        assertThat(node.has("status")).isTrue();
        assertThat(node.get("status").asInt()).isEqualTo(expectedStatus);

        assertThat(node.has("error")).isTrue();
        assertThat(node.get("error").asText()).isEqualTo(expectedError);

        assertThat(node.has("message")).isTrue();
        assertThat(node.get("message").asText()).isNotBlank();

        assertThat(node.has("path")).isTrue();
        assertThat(node.get("path").asText()).isEqualTo(expectedPath);
    }

    @Test
    @Order(31)
    @DisplayName("CORS allows requests from production Vercel frontend origin with credentials")
    void corsAllowsProductionVercelOrigin() throws Exception {
        mockMvc.perform(get("/api/events")
                        .header("Origin", "https://seat-flow-peach.vercel.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://seat-flow-peach.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @Order(32)
    @DisplayName("CORS preflight OPTIONS request from Vercel frontend returns allowed methods and headers")
    void corsPreflightFromVercelOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/holds/confirm")
                        .header("Origin", "https://seat-flow-peach.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://seat-flow-peach.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("Idempotency-Key")))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Idempotency-Key")));
    }

    @Test
    @Order(33)
    @DisplayName("CORS preserves local development origins")
    void corsPreservesLocalDevelopmentOrigins() throws Exception {
        mockMvc.perform(get("/api/events")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @Order(34)
    @DisplayName("CORS rejects untrusted origin without access-control-allow-origin header")
    void corsRejectsUntrustedOrigin() throws Exception {
        mockMvc.perform(get("/api/events")
                        .header("Origin", "https://malicious-site.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
