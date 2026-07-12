package com.lcs.finsight.models;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "financial_transaction_categories")
public class FinancialTransactionCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ftc_plan"))
    private Plan plan;

    @Enumerated(EnumType.STRING)
    private FinancialTransactionType type;

    private String description;
    private BigDecimal spendingLimit;

    public FinancialTransactionCategory() {
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() { return plan; }

    public void setPlan(Plan plan) { this.plan = plan; }

    public FinancialTransactionType getType() {
        return type;
    }

    public void setType(FinancialTransactionType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getSpendingLimit() {
        return spendingLimit;
    }

    public void setSpendingLimit(BigDecimal spendingLimit) {
        this.spendingLimit = spendingLimit;
    }
}
