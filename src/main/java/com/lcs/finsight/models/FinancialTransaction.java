package com.lcs.finsight.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_financial_transactions_users"))
    private User user;

    @ManyToOne
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_financial_transactions_financial_transaction_categories"))
    private FinancialTransactionCategory category;

    @Enumerated(EnumType.STRING)
    private FinancialTransactionType type;

    private BigDecimal amount;
    private String description;
    private String frequency;
    private Integer parcelsNumber;
    private LocalDate startDate;
    private LocalDate endDate;

    public FinancialTransaction() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public FinancialTransactionCategory getCategory() {
        return category;
    }

    public void setCategory(FinancialTransactionCategory category) {
        this.category = category;
    }

    public FinancialTransactionType getType() {
        return type;
    }

    public void setType(FinancialTransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public Integer getParcelsNumber() {
        return parcelsNumber;
    }

    public void setParcelsNumber(Integer parcelsNumber) {
        this.parcelsNumber = parcelsNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}