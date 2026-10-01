package com.smartwatch.user.dto;

/**
 * Optional body for clients that cannot send the refresh cookie.
 * The browser sends the cookie and omits this value.
 */
public record RefreshTokenRequest(String refreshToken) {
}
