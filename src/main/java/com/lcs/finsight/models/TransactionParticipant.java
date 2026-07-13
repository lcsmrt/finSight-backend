package com.lcs.finsight.models;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "transaction_participants", uniqueConstraints = @UniqueConstraint(name = "uk_txn_participants_txn_member", columnNames = {"transaction_id", "member_user_id"}))
public class TransactionParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_txn_participants_transaction"))
    private FinancialTransaction transaction;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_txn_participants_member"))
    private User member;

    @Column(name = "share_amount", nullable = false)
    private BigDecimal shareAmount;

    public TransactionParticipant() {
    }

    public Long getId() {
        return id;
    }

    public FinancialTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(FinancialTransaction transaction) {
        this.transaction = transaction;
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
