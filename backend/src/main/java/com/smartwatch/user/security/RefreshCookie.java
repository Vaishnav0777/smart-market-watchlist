package com.smartwatch.user.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * HttpOnly refresh cookie. JavaScript cannot read it.
 * SameSite=Lax keeps it on same-site requests to the auth endpoints.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "smw_refresh";
    public static final String PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;
    private final RefreshCookieProperties cookieProperties;

    public RefreshCookie(JwtProperties jwtProperties, RefreshCookieProperties cookieProperties) {
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
    }

    public ResponseCookie write(String refreshToken) {
        return base(refreshToken)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
    }

    public ResponseCookie clear() {
        return base("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite("Lax")
                .path(PATH);
    }
}
