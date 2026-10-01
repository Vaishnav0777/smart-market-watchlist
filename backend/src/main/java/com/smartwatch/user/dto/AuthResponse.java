package com.smartwatch.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        CurrentUserResponse user) {
}
