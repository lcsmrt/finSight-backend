package com.lcs.finsight.models;

import jakarta.persistence.*;

@Entity
@Table(name = "plan_memberships", uniqueConstraints = @UniqueConstraint(name = "uk_plan_memberships_plan_user", columnNames = {"plan_id", "user_id"}))
public class PlanMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_plan_memberships_plan"))
    private Plan plan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_plan_memberships_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanRole role;

    public PlanMembership() {
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public PlanRole getRole() {
        return role;
    }

    public void setRole(PlanRole role) {
        this.role = role;
    }
}
