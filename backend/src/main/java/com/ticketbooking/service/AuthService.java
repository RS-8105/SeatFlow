package com.ticketbooking.service;

import com.ticketbooking.domain.User;
import com.ticketbooking.dto.AuthResponse;
import com.ticketbooking.dto.LoginRequest;
import com.ticketbooking.dto.RegisterRequest;
import com.ticketbooking.exception.UserAlreadyExistsException;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String trimmedUsername = request.username().trim();
        String trimmedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new UserAlreadyExistsException("Username '" + trimmedUsername + "' is already taken");
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new UserAlreadyExistsException("Email '" + trimmedEmail + "' is already registered");
        }

        User user = new User(
                trimmedUsername,
                trimmedEmail,
                passwordEncoder.encode(request.password())
        );
        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);
        return new AuthResponse(
                token,
                "Bearer",
                String.valueOf(savedUser.getId()),
                savedUser.getUsername(),
                savedUser.getEmail(),
                jwtService.getExpirationMs()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.username().trim();

        // Support authentication by username or email
        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier.toLowerCase()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.password())
            );
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(
                token,
                "Bearer",
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getEmail(),
                jwtService.getExpirationMs()
        );
    }
}
