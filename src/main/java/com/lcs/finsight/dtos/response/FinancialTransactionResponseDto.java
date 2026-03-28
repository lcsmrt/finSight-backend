package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FinancialTransactionResponseDto {
    private final Long id;
    private final FinancialTransactionCategoryResponseDto category;
    private final FinancialTransactionType type;
    private final BigDecimal amount;
    private final String description;
    private final String frequency;
    private final Integer parcelsNumber;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public FinancialTransactionResponseDto(FinancialTransaction transaction) {
        this.id = transaction.getId();
        this.category = transaction.getCategory() != null
                ? new FinancialTransactionCategoryResponseDto(transaction.getCategory())
                : null;
        this.type = transaction.getType();
        this.amount = transaction.getAmount();
        this.description = transaction.getDescription();
        this.frequency = transaction.getFrequency();
        this.parcelsNumber = transaction.getParcelsNumber();
        this.startDate = transaction.getStartDate();
        this.endDate = transaction.getEndDate();
    }

    public Long getId() {
        return id;
    }

    public FinancialTransactionCategoryResponseDto getCategory() {
        return category;
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
}
