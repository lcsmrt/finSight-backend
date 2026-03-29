package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;

import java.math.BigDecimal;

public class FinancialTransactionCategoryResponseDto {
    private final Long id;
    private final FinancialTransactionType type;
    private final String description;
    private final BigDecimal spendingLimit;

    public FinancialTransactionCategoryResponseDto(FinancialTransactionCategory category) {
        this.id = category.getId();
        this.type = category.getType();
        this.description = category.getDescription();
        this.spendingLimit = category.getSpendingLimit();
    }

    public Long getId() {
        return id;
    }

    public FinancialTransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getSpendingLimit() {
        return spendingLimit;
    }
}
