package com.lcs.finsight.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recurrence_definitions", indexes = {
        @Index(name = "idx_recdef_plan_id", columnList = "plan_id"),
})
public class RecurrenceDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recdef_plan"))
    private Plan plan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_recdef_created_by"))
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_recdef_category"))
    private FinancialTransactionCategory category;

    @Column(name = "series_id", nullable = false, unique = true)
    private String seriesId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialTransactionType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private RecurrenceMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_interval")
    private RecurrenceInterval recurrenceInterval;

    private Integer parcelsNumber;

    @Column(name = "first_parcel")
    private Integer firstParcel;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_mode", nullable = false)
    private SplitMode splitMode = SplitMode.EQUAL;

    @Column(name = "generated_through")
    private LocalDate generatedThrough;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurrenceDefinitionParticipant> participants = new ArrayList<>();

    public RecurrenceDefinition() {
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

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
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

    public RecurrenceMode getMode() {
        return mode;
    }

    public void setMode(RecurrenceMode mode) {
        this.mode = mode;
    }

    public RecurrenceInterval getRecurrenceInterval() {
        return recurrenceInterval;
    }

    public void setRecurrenceInterval(RecurrenceInterval recurrenceInterval) {
        this.recurrenceInterval = recurrenceInterval;
    }

    public Integer getParcelsNumber() {
        return parcelsNumber;
    }

    public void setParcelsNumber(Integer parcelsNumber) {
        this.parcelsNumber = parcelsNumber;
    }

    public Integer getFirstParcel() {
        return firstParcel;
    }

    public void setFirstParcel(Integer firstParcel) {
        this.firstParcel = firstParcel;
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

    public LocalDate getGeneratedThrough() {
        return generatedThrough;
    }

    public void setGeneratedThrough(LocalDate generatedThrough) {
        this.generatedThrough = generatedThrough;
    }

    public List<RecurrenceDefinitionParticipant> getParticipants() {
        return participants;
    }
}
