package com.smartwatch.watchlist.entity;

import com.smartwatch.common.AuditableEntity;
import com.smartwatch.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * A named list of instruments that belongs to one user.
 *
 * <p>Names are unique per user. Two users may each have a list called
 * "Long Term".
 */
@Entity
@Table(
        name = "watchlists",
        uniqueConstraints = @UniqueConstraint(name = "uk_watchlists_user_name", columnNames = {"user_id", "name"}))
public class Watchlist extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @OneToMany(mappedBy = "watchlist", fetch = FetchType.LAZY)
    private List<WatchlistItem> items = new ArrayList<>();

    protected Watchlist() {
    }

    public Watchlist(User user, String name) {
        this.user = user;
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public void renameTo(String name) {
        this.name = name;
    }

    public List<WatchlistItem> getItems() {
        return items;
    }

    @Override
    public String toString() {
        return "Watchlist{id=" + getId() + ", name='" + name + "'}";
    }
}
