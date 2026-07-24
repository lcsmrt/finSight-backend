package com.lcs.finsight.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "financial_transactions", indexes = {
        @Index(name = "idx_financial_transactions_start_date", columnList = "start_date"),
        @Index(name = "idx_ft_plan_id", columnList = "plan_id"),
        @Index(name = "idx_ft_plan_id_start_date", columnList = "plan_id, start_date"),
        @Index(name = "idx_ft_plan_id_series_id", columnList = "plan_id, series_id"),
})
public class FinancialTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ft_plan"))
    private Plan plan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_ft_created_by"))
    private User createdBy;

    @ManyToOne
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_financial_transactions_financial_transaction_categories"))
    private FinancialTransactionCategory category;

    @Enumerated(EnumType.STRING)
    private FinancialTransactionType type;

    private BigDecimal amount;
    private String description;
    private String externalId;
    @Column(name = "series_id")
    private String seriesId;
    private Integer parcelsNumber;

    @ManyToOne(optional = true)
    @JoinColumn(name = "recurrence_definition_id", foreignKey = @ForeignKey(name = "fk_ft_recurrence_definition"))
    private RecurrenceDefinition recurrenceDefinition;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_mode", nullable = false)
    private SplitMode splitMode = SplitMode.EQUAL;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionItem> items = new ArrayList<>();

    public FinancialTransaction() {
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

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public Integer getParcelsNumber() {
        return parcelsNumber;
    }

    public void setParcelsNumber(Integer parcelsNumber) {
        this.parcelsNumber = parcelsNumber;
    }

    public RecurrenceDefinition getRecurrenceDefinition() {
        return recurrenceDefinition;
    }

    public void setRecurrenceDefinition(RecurrenceDefinition recurrenceDefinition) {
        this.recurrenceDefinition = recurrenceDefinition;
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

    public SplitMode getSplitMode() {
        return splitMode;
    }

    public void setSplitMode(SplitMode splitMode) {
        this.splitMode = splitMode;
    }

    public List<TransactionParticipant> getParticipants() {
        return participants;
    }

    public List<TransactionItem> getItems() {
        return items;
    }
}