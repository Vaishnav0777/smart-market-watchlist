package com.smartwatch.user.controller;

import com.smartwatch.common.web.ApiException;
import com.smartwatch.user.dto.AuthResponse;
import com.smartwatch.user.dto.CurrentUserResponse;
import com.smartwatch.user.dto.IssuedSession;
import com.smartwatch.user.dto.LoginRequest;
import com.smartwatch.user.dto.LogoutRequest;
import com.smartwatch.user.dto.RefreshTokenRequest;
import com.smartwatch.user.dto.RegisterRequest;
import com.smartwatch.user.security.RefreshCookie;
import com.smartwatch.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return tokenResponse(HttpStatus.CREATED, authService.register(request.email(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(HttpStatus.OK, authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest request) {
        return tokenResponse(HttpStatus.OK, authService.refresh(requireRefreshToken(cookieToken, request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken,
            @RequestBody(required = false) LogoutRequest request) {
        String token = firstToken(cookieToken, request == null ? null : request.refreshToken());
        if (token != null) {
            authService.logout(token);
        }
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.currentUser());
    }

    private ResponseEntity<AuthResponse> tokenResponse(HttpStatus status, IssuedSession issued) {
        ResponseCookie cookie = refreshCookie.write(issued.refreshToken());
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(issued.response());
    }

    private static String requireRefreshToken(String cookieToken, RefreshTokenRequest request) {
        String token = firstToken(cookieToken, request == null ? null : request.refreshToken());
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        return token;
    }

    private static String firstToken(String cookieToken, String bodyToken) {
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        if (bodyToken != null && !bodyToken.isBlank()) {
            return bodyToken;
        }
        return null;
    }
}
