package com.smartwatch.user.dto;

/**
 * Optional body. Logout prefers the HttpOnly refresh cookie.
 */
public record LogoutRequest(String refreshToken) {
}
