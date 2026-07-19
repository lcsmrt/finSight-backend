package com.lcs.finsight.dtos.request;

import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.RecurrenceInterval;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SeriesEditScope;
import com.lcs.finsight.models.SplitMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SeriesEditRequestDto {

    @NotNull(message = "Transaction type cannot be null.")
    private FinancialTransactionType type;

    @NotNull(message = "Amount cannot be null.")
    @Positive(message = "Amount must be positive.")
    private BigDecimal amount;

    @NotBlank(message = "Description cannot be blank.")
    private String description;

    private Long categoryId;

    @NotNull(message = "Recurrence mode cannot be null.")
    private RecurrenceMode mode;

    // For installments, the month of the current parcel (parcel {@code currentParcel}); for recurring, the first occurrence.
    @NotNull(message = "Start date cannot be null.")
    private LocalDate startDate;

    @Min(value = 2, message = "Parcels number must be at least 2.")
    @Max(value = 120, message = "Parcels number cannot exceed 120.")
    private Integer parcelsNumber;

    // The first parcel to generate (1-based, default 1); generation produces parcels currentParcel..parcelsNumber.
    @Min(value = 1, message = "Current parcel must be at least 1.")
    private Integer currentParcel;

    private RecurrenceInterval interval;

    private LocalDate endDate;

    private SplitMode splitMode;

    @Valid
    private List<ParticipantDto> participants;

    @NotNull(message = "Edit scope cannot be null.")
    private SeriesEditScope scope;

    // Required (validated at the service layer) for THIS_ONE and THIS_AND_FOLLOWING scopes.
    private Long pivotOccurrenceId;

    public FinancialTransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public RecurrenceMode getMode() {
        return mode;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public Integer getParcelsNumber() {
        return parcelsNumber;
    }

    public Integer getCurrentParcel() {
        return currentParcel;
    }

    public RecurrenceInterval getInterval() {
        return interval;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public SplitMode getSplitMode() {
        return splitMode;
    }

    public List<ParticipantDto> getParticipants() {
        return participants;
    }

    public SeriesEditScope getScope() {
        return scope;
    }

    public Long getPivotOccurrenceId() {
        return pivotOccurrenceId;
    }
}
