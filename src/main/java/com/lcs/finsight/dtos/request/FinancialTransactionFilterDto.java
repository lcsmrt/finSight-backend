package com.lcs.finsight.dtos.request;

import com.lcs.finsight.models.FinancialTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FinancialTransactionFilterDto extends PaginatedFilterDto {

    private FinancialTransactionType type;
    private Long categoryId;
    private String description;
    private LocalDate startDateFrom;
    private LocalDate startDateTo;
    private BigDecimal amountMin;
    private BigDecimal amountMax;

    public FinancialTransactionFilterDto() {
        super("startDate", "desc");
    }

    public FinancialTransactionType getType() { return type; }
    public void setType(FinancialTransactionType type) { this.type = type; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getStartDateFrom() { return startDateFrom; }
    public void setStartDateFrom(LocalDate startDateFrom) { this.startDateFrom = startDateFrom; }

    public LocalDate getStartDateTo() { return startDateTo; }
    public void setStartDateTo(LocalDate startDateTo) { this.startDateTo = startDateTo; }

    public BigDecimal getAmountMin() { return amountMin; }
    public void setAmountMin(BigDecimal amountMin) { this.amountMin = amountMin; }

    public BigDecimal getAmountMax() { return amountMax; }
    public void setAmountMax(BigDecimal amountMax) { this.amountMax = amountMax; }
}
