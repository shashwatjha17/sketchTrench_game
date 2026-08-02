package com.sketchtrench.common.security;

import com.sketchtrench.auth.security.UserPrincipal;
import com.sketchtrench.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessors for the currently authenticated user, used by controllers/services. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal;
    }

    public static Long currentUserId() {
        return currentUser().id();
    }

    public static String currentUsername() {
        return currentUser().username();
    }
}
