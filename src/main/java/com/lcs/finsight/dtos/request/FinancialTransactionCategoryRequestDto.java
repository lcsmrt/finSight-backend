package com.lcs.finsight.dtos.request;

import com.lcs.finsight.models.FinancialTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class FinancialTransactionCategoryRequestDto {

	@NotNull(message = "Type cannot be null.")
	private FinancialTransactionType type;

	@NotBlank(message = "Description cannot be blank.")
	private String description;

	private BigDecimal spendingLimit;

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
