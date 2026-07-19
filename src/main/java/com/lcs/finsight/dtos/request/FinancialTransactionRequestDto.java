package com.lcs.finsight.dtos.request;

import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.SplitMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FinancialTransactionRequestDto {

    private Long categoryId;

    @NotNull(message = "Transaction type cannot be null.")
    private FinancialTransactionType type;

    @NotNull(message = "Amount cannot be null.")
    @Positive(message = "Amount must be positive.")
    private BigDecimal amount;

    @NotBlank(message = "Description cannot be blank.")
    private String description;

    private String frequency;
    private Integer parcelsNumber;

    @NotNull(message = "Start date cannot be null.")
    private LocalDate startDate;
    private LocalDate endDate;

    private SplitMode splitMode;

    @Valid
    private List<ParticipantDto> participants;

    @Valid
    private List<ItemDto> items;

    public Long getCategoryId() {
        return categoryId;
    }

    public FinancialTransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getFrequency() {
        return frequency;
    }

    public Integer getParcelsNumber() {
        return parcelsNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
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

    public List<ItemDto> getItems() {
        return items;
    }
}
