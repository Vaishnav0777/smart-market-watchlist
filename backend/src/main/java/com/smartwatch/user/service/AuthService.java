package com.smartwatch.user.service;

import com.smartwatch.common.web.ApiException;
import com.smartwatch.user.dto.AuthResponse;
import com.smartwatch.user.dto.CurrentUserResponse;
import com.smartwatch.user.entity.User;
import com.smartwatch.user.security.JwtTokenService;
import com.smartwatch.user.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserService userService,
            RefreshTokenService refreshTokenService,
            JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse register(String email, String rawPassword, String displayName) {
        User user = userService.register(email, rawPassword, displayName);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        User user = userService.authenticate(email, rawPassword);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        User user = refreshTokenService.rotate(rawRefreshToken);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        User user = userService.requireById(principal.getId());
        return CurrentUserResponse.from(user);
    }

    private AuthResponse issueTokens(User user) {
        String refreshToken = refreshTokenService.issue(user);
        String accessToken = jwtTokenService.issueAccessToken(user.getId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenService.accessTokenTtlSeconds(),
                CurrentUserResponse.from(user));
    }
}
