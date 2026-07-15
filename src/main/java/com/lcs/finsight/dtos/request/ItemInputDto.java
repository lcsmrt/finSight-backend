package com.lcs.finsight.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class ItemInputDto {

    @NotBlank(message = "Item description cannot be blank.")
    private String description;

    @NotNull(message = "Item amount cannot be null.")
    @Positive(message = "Item amount must be positive.")
    private BigDecimal amount;

    private Long categoryId;

    @Positive(message = "Item quantity must be positive.")
    private Integer quantity;

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
