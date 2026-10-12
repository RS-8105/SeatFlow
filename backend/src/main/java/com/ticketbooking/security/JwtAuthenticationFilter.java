package com.ticketbooking.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public static final String DEFAULT_DEV_DEMO_KEY = "demo-secret-key-ticketbooking";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final String demoApiKey;
    private final boolean configuredAllowDemoHeader;
    private final Environment environment;

    private boolean effectiveAllowDemoHeader;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            @Value("${ticketbooking.demo.api-key:demo-secret-key-ticketbooking}") String demoApiKey,
            @Value("${ticketbooking.demo.allow-demo-header:true}") boolean configuredAllowDemoHeader,
            Environment environment
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.demoApiKey = demoApiKey;
        this.configuredAllowDemoHeader = configuredAllowDemoHeader;
        this.environment = environment;
    }

    @PostConstruct
    public void validateDemoConfiguration() {
        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (isProduction) {
            if (configuredAllowDemoHeader) {
                if (demoApiKey == null || demoApiKey.isBlank() || DEFAULT_DEV_DEMO_KEY.equals(demoApiKey.trim())) {
                    log.error("CRITICAL: Default or blank demo API key detected in production profile! Disabling X-Demo-Key header authentication.");
                    this.effectiveAllowDemoHeader = false;
                } else {
                    this.effectiveAllowDemoHeader = true;
                }
            } else {
                this.effectiveAllowDemoHeader = false;
            }
        } else {
            this.effectiveAllowDemoHeader = configuredAllowDemoHeader;
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        // 1. Process Bearer JWT token if present
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7).trim();
            if (!jwt.isBlank()) {
                try {
                    String username = jwtService.extractUsername(jwt);
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                        if (jwtService.isTokenValid(jwt, userDetails)) {
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("JWT authentication filter exception: {}", ex.getMessage());
                }
            }
        }

        // 2. Demo API key guard for /api/demo/** endpoints to preserve demo workflow
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && effectiveAllowDemoHeader
                && demoApiKey != null
                && !demoApiKey.isBlank()
                && isDemoPath(request)) {
            String demoHeader = request.getHeader("X-Demo-Key");
            if (demoHeader != null && !demoHeader.isBlank()) {
                byte[] headerBytes = demoHeader.trim().getBytes(StandardCharsets.UTF_8);
                byte[] expectedBytes = demoApiKey.trim().getBytes(StandardCharsets.UTF_8);

                if (MessageDigest.isEqual(headerBytes, expectedBytes)) {
                    UsernamePasswordAuthenticationToken demoToken = new UsernamePasswordAuthenticationToken(
                            "demo-admin",
                            null,
                            Collections.emptyList()
                    );
                    demoToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(demoToken);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDemoPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && (servletPath.equals("/api/demo") || servletPath.startsWith("/api/demo/"))) {
            return true;
        }
        String requestUri = request.getRequestURI();
        return requestUri != null && (requestUri.equals("/api/demo") || requestUri.startsWith("/api/demo/"));
    }
}
