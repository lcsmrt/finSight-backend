package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;

public class CategoryBreakdownDto {
    private final String categoryName;
    private final BigDecimal spent;
    private final BigDecimal limit;

    public CategoryBreakdownDto(String categoryName, BigDecimal spent, BigDecimal limit) {
        this.categoryName = categoryName;
        this.spent = spent;
        this.limit = limit;
    }

    public String getCategoryName() { return categoryName; }
    public BigDecimal getSpent() { return spent; }
    public BigDecimal getLimit() { return limit; }
}
