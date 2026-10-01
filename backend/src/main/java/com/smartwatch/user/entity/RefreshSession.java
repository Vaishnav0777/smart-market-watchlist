package com.smartwatch.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.Hibernate;

import java.time.Instant;
import java.util.UUID;

/**
 * A revocable refresh session.
 *
 * <p>Only the SHA-256 digest of the opaque refresh token is stored.
 * This entity does not use {@code AuditableEntity} because expiry and
 * revocation are session fields, not a generic updated-at audit column.
 */
@Entity
@Table(
        name = "refresh_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_sessions_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_refresh_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_sessions_expires_at", columnList = "expires_at")
        })
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshSession() {
    }

    public RefreshSession(User user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void markUsed(Instant when) {
        this.lastUsedAt = when;
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        RefreshSession that = (RefreshSession) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "RefreshSession{id=" + id + "}";
    }
}
