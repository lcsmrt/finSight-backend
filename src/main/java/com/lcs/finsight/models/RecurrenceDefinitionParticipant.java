package com.lcs.finsight.models;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "recurrence_definition_participants", uniqueConstraints = @UniqueConstraint(name = "uk_recdef_participants_def_member", columnNames = {"recurrence_definition_id", "member_user_id"}))
public class RecurrenceDefinitionParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recurrence_definition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recdef_participants_definition"))
    private RecurrenceDefinition definition;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recdef_participants_member"))
    private User member;

    @Column(name = "share_amount", nullable = false)
    private BigDecimal shareAmount;

    public RecurrenceDefinitionParticipant() {
    }

    public Long getId() {
        return id;
    }

    public RecurrenceDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(RecurrenceDefinition definition) {
        this.definition = definition;
    }

    public User getMember() {
        return member;
    }

    public void setMember(User member) {
        this.member = member;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    public void setShareAmount(BigDecimal shareAmount) {
        this.shareAmount = shareAmount;
    }
}
