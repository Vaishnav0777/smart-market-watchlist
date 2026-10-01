package com.smartwatch.watchlist.entity;

import com.smartwatch.user.entity.User;
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
import org.hibernate.Hibernate;

import java.time.Instant;
import java.util.UUID;

/**
 * An explicit "I checked this watchlist" cursor.
 *
 * <p>Reading the watchlist does not create one of these.
 */
@Entity
@Table(
        name = "watchlist_checks",
        indexes = @Index(name = "idx_watchlist_checks_watchlist_checked", columnList = "watchlist_id, checked_at"))
public class WatchlistCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    protected WatchlistCheck() {
    }

    public WatchlistCheck(Watchlist watchlist, User user, Instant checkedAt) {
        this.watchlist = watchlist;
        this.user = user;
        this.checkedAt = checkedAt;
    }

    public UUID getId() {
        return id;
    }

    public Watchlist getWatchlist() {
        return watchlist;
    }

    public User getUser() {
        return user;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        WatchlistCheck that = (WatchlistCheck) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "WatchlistCheck{id=" + id + "}";
    }
}
