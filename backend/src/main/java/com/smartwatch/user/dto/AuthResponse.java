package com.smartwatch.user.dto;

/**
 * Access credentials returned to the caller.
 * The refresh token is not included. It is sent only as an HttpOnly cookie.
 */
public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        CurrentUserResponse user) {
}
