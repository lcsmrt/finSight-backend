package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;
import java.util.List;

public class DashboardSummaryDto {
    private final BigDecimal totalIncome;
    private final BigDecimal totalExpenses;
    private final BigDecimal netBalance;
    private final List<CategoryBreakdownDto> categoryBreakdown;
    private final List<MonthlyTrendDto> monthlyTrend;

    public DashboardSummaryDto(
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netBalance,
            List<CategoryBreakdownDto> categoryBreakdown,
            List<MonthlyTrendDto> monthlyTrend
    ) {
        this.totalIncome = totalIncome;
        this.totalExpenses = totalExpenses;
        this.netBalance = netBalance;
        this.categoryBreakdown = categoryBreakdown;
        this.monthlyTrend = monthlyTrend;
    }

    public BigDecimal getTotalIncome() { return totalIncome; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public BigDecimal getNetBalance() { return netBalance; }
    public List<CategoryBreakdownDto> getCategoryBreakdown() { return categoryBreakdown; }
    public List<MonthlyTrendDto> getMonthlyTrend() { return monthlyTrend; }
}
