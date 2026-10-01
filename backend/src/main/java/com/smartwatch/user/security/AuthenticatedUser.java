package com.smartwatch.user.security;

import com.smartwatch.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The user id from the authenticated access token.
 *
 * <p>Callers must use this instead of a client-supplied owner id.
 */
@Component
public class AuthenticatedUser {

    public UUID requireId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal.getId();
    }
}
