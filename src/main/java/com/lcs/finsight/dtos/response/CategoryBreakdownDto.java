package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CategoryBreakdownDto {
    private final String categoryName;
    private final BigDecimal spent;
    private final BigDecimal limit;
    private final BigDecimal remaining;
    private final BigDecimal percentUsed;
    private final Boolean overLimit;

    public CategoryBreakdownDto(String categoryName, BigDecimal spent, BigDecimal limit) {
        this.categoryName = categoryName;
        this.spent = spent;
        this.limit = limit;

        if (limit == null) {
            this.remaining = null;
            this.percentUsed = null;
            this.overLimit = null;
        } else {
            this.remaining = limit.subtract(spent);
            this.percentUsed = limit.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : spent.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
            this.overLimit = spent.compareTo(limit) > 0;
        }
    }

    public String getCategoryName() { return categoryName; }
    public BigDecimal getSpent() { return spent; }
    public BigDecimal getLimit() { return limit; }
    public BigDecimal getRemaining() { return remaining; }
    public BigDecimal getPercentUsed() { return percentUsed; }
    public Boolean getOverLimit() { return overLimit; }
}
