package com.smartwatch.user.service;

import com.smartwatch.common.web.ApiException;
import com.smartwatch.user.entity.RefreshSession;
import com.smartwatch.user.entity.User;
import com.smartwatch.user.repository.RefreshSessionRepository;
import com.smartwatch.user.security.JwtProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private final RefreshSessionRepository refreshSessionRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshSessionRepository refreshSessionRepository,
            JwtProperties jwtProperties,
            Clock clock) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        Instant now = clock.instant();
        String rawToken = newRawToken();
        RefreshSession session = new RefreshSession(user, hash(rawToken), now, now.plus(jwtProperties.refreshTokenTtl()));
        refreshSessionRepository.save(session);
        return rawToken;
    }

    @Transactional
    public User rotate(String rawToken) {
        RefreshSession session = requireUsable(rawToken);
        Instant now = clock.instant();
        session.markUsed(now);
        session.revoke(now);
        return session.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshSessionRepository.findByTokenHash(hash(rawToken)).ifPresent(session -> {
            if (!session.isRevoked()) {
                session.revoke(clock.instant());
            }
        });
    }

    private RefreshSession requireUsable(String rawToken) {
        RefreshSession session = refreshSessionRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (session.isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }
        if (!session.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }
        if (!session.getUser().isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        return session;
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
