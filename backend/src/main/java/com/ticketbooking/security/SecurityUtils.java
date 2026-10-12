package com.ticketbooking.security;

import com.ticketbooking.domain.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * Utility for resolving canonical authenticated user identities.
 * Enforces the use of the stable database user ID rather than mutable usernames.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Resolves the canonical user ID (database ID as String) from the authenticated principal.
     *
     * @param authentication the active Spring Security Authentication
     * @return the canonical user identifier (e.g., "1", "2")
     * @throws AccessDeniedException if authentication is missing or unauthenticated
     */
    public static String getCanonicalUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated request: cannot derive user identity");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            if (user.getId() != null) {
                return String.valueOf(user.getId());
            }
            return user.getUsername();
        }

        // Fallback for special non-User authenticated principals (e.g., demo-admin)
        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }

        throw new AccessDeniedException("Unable to determine authenticated user identity");
    }
}
