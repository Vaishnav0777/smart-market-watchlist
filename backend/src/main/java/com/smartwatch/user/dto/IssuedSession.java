package com.smartwatch.user.dto;

/**
 * The access-token response plus the raw refresh token that must be stored
 * in an HttpOnly cookie and must not be copied into the JSON body.
 */
public record IssuedSession(AuthResponse response, String refreshToken) {
}
