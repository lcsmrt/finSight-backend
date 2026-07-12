package com.lcs.finsight.services;

import com.lcs.finsight.dtos.response.CategoryBreakdownDto;
import com.lcs.finsight.dtos.response.DashboardSummaryDto;
import com.lcs.finsight.dtos.response.MonthlyTrendDto;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.security.PlanContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final FinancialTransactionRepository financialTransactionRepository;

    public DashboardService(FinancialTransactionRepository financialTransactionRepository) {
        this.financialTransactionRepository = financialTransactionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary(PlanContext ctx, LocalDate startDate, LocalDate endDate) {
        Plan plan = ctx.getPlan();

        BigDecimal totalIncome = financialTransactionRepository.sumByPlanAndTypeAndDateRange(
                plan, FinancialTransactionType.CREDIT, startDate, endDate);

        BigDecimal totalExpenses = financialTransactionRepository.sumByPlanAndTypeAndDateRange(
                plan, FinancialTransactionType.DEBIT, startDate, endDate);

        BigDecimal netBalance = totalIncome.subtract(totalExpenses);

        List<CategoryBreakdownDto> categoryBreakdown = buildCategoryBreakdown(plan, startDate, endDate);
        List<MonthlyTrendDto> monthlyTrend = buildMonthlyTrend(plan, startDate, endDate);

        return new DashboardSummaryDto(totalIncome, totalExpenses, netBalance, categoryBreakdown, monthlyTrend);
    }

    private List<CategoryBreakdownDto> buildCategoryBreakdown(Plan plan, LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = financialTransactionRepository.findCategoryBreakdown(
                plan, FinancialTransactionType.DEBIT, startDate, endDate);

        List<CategoryBreakdownDto> result = new ArrayList<>();
        for (Object[] row : rows) {
            String categoryName = (String) row[0];
            BigDecimal spent = (BigDecimal) row[1];
            BigDecimal limit = (BigDecimal) row[2];
            result.add(new CategoryBreakdownDto(categoryName, spent, limit));
        }
        return result;
    }

    private List<MonthlyTrendDto> buildMonthlyTrend(Plan plan, LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = financialTransactionRepository.findMonthlyTrend(plan, startDate, endDate);

        Map<String, MonthlyTrendDto> trendMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            FinancialTransactionType type = (FinancialTransactionType) row[2];
            BigDecimal amount = (BigDecimal) row[3];

            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k -> new MonthlyTrendDto(year, month));

            MonthlyTrendDto trend = trendMap.get(key);
            if (type == FinancialTransactionType.CREDIT) {
                trend.setIncome(amount);
            } else {
                trend.setExpenses(amount);
            }
        }
        return new ArrayList<>(trendMap.values());
    }
}
