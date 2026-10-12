package com.ticketbooking.security;

import com.ticketbooking.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String DEFAULT_DEV_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final String secretKeyString;
    private final long jwtExpirationMs;
    private final Environment environment;

    public JwtService(
            @Value("${ticketbooking.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secretKeyString,
            @Value("${ticketbooking.jwt.expiration-ms:86400000}") long jwtExpirationMs,
            Environment environment
    ) {
        this.secretKeyString = secretKeyString;
        this.jwtExpirationMs = jwtExpirationMs;
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (isProduction) {
            if (secretKeyString == null || secretKeyString.isBlank() || DEFAULT_DEV_SECRET.equals(secretKeyString.trim())) {
                throw new IllegalStateException(
                        "Production startup failed: A custom, cryptographically secure JWT secret must be provided via the JWT_SECRET environment variable. The default development secret is strictly forbidden in production."
                );
            }
            byte[] keyBytes = getRawKeyBytes(secretKeyString);
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "Production startup failed: The JWT secret must provide at least 256 bits (32 bytes) of key material for HMAC-SHA256."
                );
            }
        } else {
            if (DEFAULT_DEV_SECRET.equals(secretKeyString)) {
                log.warn("Using default development JWT secret. Set the JWT_SECRET environment variable for production environments.");
            }
        }
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        return buildToken(claims, user.getUsername(), jwtExpirationMs);
    }

    public String generateToken(String username, Map<String, Object> extraClaims) {
        return buildToken(extraClaims, username, jwtExpirationMs);
    }

    public String extractUsername(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }

    public String extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object userId = claims.get("userId");
            return userId != null ? String.valueOf(userId) : null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Failed to extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username != null && username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractAllClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    private byte[] getRawKeyBytes(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded != null && decoded.length >= 32) {
                return decoded;
            }
        } catch (Exception ex) {
            // Not a base64 encoded string, fall back to utf-8 bytes
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = getRawKeyBytes(secretKeyString);

        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(getSigningKey())
                .compact();
    }
}
