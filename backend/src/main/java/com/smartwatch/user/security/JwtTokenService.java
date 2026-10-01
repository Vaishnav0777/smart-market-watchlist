package com.smartwatch.user.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Issues and validates short-lived HMAC-SHA256 access tokens.
 *
 * <p>Signing uses Spring Security's Nimbus {@link JwtEncoder}. Claims are the
 * user id ({@code sub}) and {@code token_type=access}.
 */
@Service
public class JwtTokenService {

    static final String TOKEN_TYPE_CLAIM = "token_type";
    static final String ACCESS_TOKEN_TYPE = "access";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = secretBytes(properties.secret());
        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        ImmutableSecret<SecurityContext> jwk = new ImmutableSecret<>(key);
        this.jwtEncoder = new NimbusJwtEncoder(jwk);
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public String issueAccessToken(UUID userId) {
        Instant issuedAt = Instant.now();
        return issueAccessToken(userId, issuedAt, issuedAt.plus(properties.accessTokenTtl()));
    }

    public String issueAccessToken(UUID userId, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public UUID parseUserId(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaim(TOKEN_TYPE_CLAIM))) {
                throw new InvalidAccessTokenException("Invalid access token");
            }
            return UUID.fromString(jwt.getSubject());
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (JwtException exception) {
            String detail = exception.getMessage() == null
                    ? ""
                    : exception.getMessage().toLowerCase(Locale.ROOT);
            if (detail.contains("expired")) {
                throw new InvalidAccessTokenException("Access token has expired");
            }
            throw new InvalidAccessTokenException("Invalid access token");
        } catch (IllegalArgumentException exception) {
            throw new InvalidAccessTokenException("Invalid access token");
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    private static byte[] secretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes");
        }
        byte[] bytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        return bytes;
    }
}
