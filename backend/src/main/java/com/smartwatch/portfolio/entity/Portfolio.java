package com.smartwatch.portfolio.entity;

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
 * A named book of holdings that belongs to one user.
 *
 * <p>Names are unique per user.
 */
@Entity
@Table(
        name = "portfolios",
        uniqueConstraints = @UniqueConstraint(name = "uk_portfolios_user_name", columnNames = {"user_id", "name"}))
public class Portfolio extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @OneToMany(mappedBy = "portfolio", fetch = FetchType.LAZY)
    private List<Position> positions = new ArrayList<>();

    protected Portfolio() {
    }

    public Portfolio(User user, String name) {
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

    public List<Position> getPositions() {
        return positions;
    }

    @Override
    public String toString() {
        return "Portfolio{id=" + getId() + ", name='" + name + "'}";
    }
}
