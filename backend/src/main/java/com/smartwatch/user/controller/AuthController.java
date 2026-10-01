package com.smartwatch.user.controller;

import com.smartwatch.user.dto.AuthResponse;
import com.smartwatch.user.dto.CurrentUserResponse;
import com.smartwatch.user.dto.LoginRequest;
import com.smartwatch.user.dto.LogoutRequest;
import com.smartwatch.user.dto.RefreshTokenRequest;
import com.smartwatch.user.dto.RegisterRequest;
import com.smartwatch.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(HttpStatus.OK, authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.currentUser());
    }

    private static ResponseEntity<AuthResponse> tokenResponse(HttpStatus status, AuthResponse body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
