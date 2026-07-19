package com.lcs.finsight.dtos.response;

import java.math.BigDecimal;
import java.util.List;

public class DashboardSummaryResponseDto {
    private final BigDecimal totalIncome;
    private final BigDecimal totalExpenses;
    private final BigDecimal netBalance;
    private final List<CategoryBreakdownDto> categoryBreakdown;
    private final List<MonthlyTrendDto> monthlyTrend;
    private final List<PersonBreakdownDto> personBreakdown;

    public DashboardSummaryResponseDto(
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netBalance,
            List<CategoryBreakdownDto> categoryBreakdown,
            List<MonthlyTrendDto> monthlyTrend,
            List<PersonBreakdownDto> personBreakdown
    ) {
        this.totalIncome = totalIncome;
        this.totalExpenses = totalExpenses;
        this.netBalance = netBalance;
        this.categoryBreakdown = categoryBreakdown;
        this.monthlyTrend = monthlyTrend;
        this.personBreakdown = personBreakdown;
    }

    public BigDecimal getTotalIncome() { return totalIncome; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public BigDecimal getNetBalance() { return netBalance; }
    public List<CategoryBreakdownDto> getCategoryBreakdown() { return categoryBreakdown; }
    public List<MonthlyTrendDto> getMonthlyTrend() { return monthlyTrend; }
    public List<PersonBreakdownDto> getPersonBreakdown() { return personBreakdown; }
}
