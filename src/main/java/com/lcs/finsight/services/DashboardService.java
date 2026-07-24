package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.DashboardFilterDto;
import com.lcs.finsight.dtos.response.CategoryBreakdownDto;
import com.lcs.finsight.dtos.response.DashboardSummaryResponseDto;
import com.lcs.finsight.dtos.response.MonthlyTrendDto;
import com.lcs.finsight.dtos.response.PersonBreakdownDto;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.repositories.FinancialTransactionCategoryRepository;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.security.PlanContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialTransactionCategoryRepository financialTransactionCategoryRepository;
    private final CategoryBreakdownAssembler categoryBreakdownAssembler;
    private final OpenEndedSeriesTopUpService openEndedSeriesTopUpService;
    private final Clock clock;

    public DashboardService(FinancialTransactionRepository financialTransactionRepository,
                             FinancialTransactionCategoryRepository financialTransactionCategoryRepository,
                             CategoryBreakdownAssembler categoryBreakdownAssembler,
                             OpenEndedSeriesTopUpService openEndedSeriesTopUpService,
                             Clock clock) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionCategoryRepository = financialTransactionCategoryRepository;
        this.categoryBreakdownAssembler = categoryBreakdownAssembler;
        this.openEndedSeriesTopUpService = openEndedSeriesTopUpService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponseDto getSummary(DashboardFilterDto filter, PlanContext ctx) {
        Plan plan = ctx.getPlan();
        openEndedSeriesTopUpService.topUp(plan, LocalDate.now(clock));

        LocalDate startDate = filter.getStartDate();
        LocalDate endDate = filter.getEndDate();
        Long memberId = filter.getMemberId();

        BigDecimal totalIncome = financialTransactionRepository.sumByPlanAndTypeAndDateRange(
                plan, FinancialTransactionType.CREDIT, startDate, endDate, memberId);

        BigDecimal totalExpenses = financialTransactionRepository.sumByPlanAndTypeAndDateRange(
                plan, FinancialTransactionType.DEBIT, startDate, endDate, memberId);

        BigDecimal netBalance = totalIncome.subtract(totalExpenses);

        List<CategoryBreakdownDto> categoryBreakdown = buildCategoryBreakdown(plan, startDate, endDate, memberId);
        List<MonthlyTrendDto> monthlyTrend = buildMonthlyTrend(plan, startDate, endDate, memberId);
        List<PersonBreakdownDto> personBreakdown = buildPersonBreakdown(plan, startDate, endDate, memberId);

        return new DashboardSummaryResponseDto(totalIncome, totalExpenses, netBalance, categoryBreakdown, monthlyTrend, personBreakdown);
    }

    private List<CategoryBreakdownDto> buildCategoryBreakdown(Plan plan, LocalDate startDate, LocalDate endDate, Long memberId) {
        List<Object[]> rowsA = financialTransactionRepository.findCategoryBreakdown(
                plan, FinancialTransactionType.DEBIT, startDate, endDate, memberId);
        // Item-sum queries (B/I) remain plan-total: items are an independent axis from participants and
        // carry no share attribution. When filtered by member, A is member-scoped while B/I are full item
        // amounts — a documented Round-2 gap. It is inert today (no live items) and cannot affect the
        // unfiltered numbers (memberId == null path is unchanged).
        List<Object[]> rowsB = financialTransactionRepository.findCategorizedItemSumsByParentCategory(
                plan, FinancialTransactionType.DEBIT, startDate, endDate);
        List<Object[]> rowsI = financialTransactionRepository.findCategorizedItemSumsByItemCategory(
                plan, FinancialTransactionType.DEBIT, startDate, endDate);
        List<FinancialTransactionCategory> limitCategories = financialTransactionCategoryRepository.findAllByPlan(plan);

        return categoryBreakdownAssembler.assemble(rowsA, rowsB, rowsI, limitCategories);
    }

    private record MonthKey(int year, int month) {}

    private List<MonthlyTrendDto> buildMonthlyTrend(Plan plan, LocalDate startDate, LocalDate endDate, Long memberId) {
        List<Object[]> rows = financialTransactionRepository.findMonthlyTrend(plan, startDate, endDate, memberId);

        Map<MonthKey, BigDecimal[]> amountsByMonth = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            FinancialTransactionType type = (FinancialTransactionType) row[2];
            BigDecimal amount = (BigDecimal) row[3];

            BigDecimal[] incomeAndExpenses = amountsByMonth.computeIfAbsent(
                    new MonthKey(year, month), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (type == FinancialTransactionType.CREDIT) {
                incomeAndExpenses[0] = amount;
            } else {
                incomeAndExpenses[1] = amount;
            }
        }

        List<MonthlyTrendDto> trend = new ArrayList<>();
        for (Map.Entry<MonthKey, BigDecimal[]> entry : amountsByMonth.entrySet()) {
            trend.add(new MonthlyTrendDto(entry.getKey().year(), entry.getKey().month(),
                    entry.getValue()[0], entry.getValue()[1]));
        }
        return trend;
    }

    private List<PersonBreakdownDto> buildPersonBreakdown(Plan plan, LocalDate startDate, LocalDate endDate, Long memberId) {
        List<Object[]> rows = financialTransactionRepository.findPersonBreakdown(plan, startDate, endDate, memberId);

        Map<Long, String> namesByUserId = new LinkedHashMap<>();
        Map<Long, BigDecimal[]> amountsByUserId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long userId = ((Number) row[0]).longValue();
            String name = (String) row[1];
            FinancialTransactionType type = (FinancialTransactionType) row[2];
            BigDecimal amount = (BigDecimal) row[3];

            namesByUserId.putIfAbsent(userId, name);
            BigDecimal[] incomeAndExpense = amountsByUserId.computeIfAbsent(
                    userId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (type == FinancialTransactionType.CREDIT) {
                incomeAndExpense[0] = amount;
            } else {
                incomeAndExpense[1] = amount;
            }
        }

        List<PersonBreakdownDto> breakdown = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> entry : amountsByUserId.entrySet()) {
            Long userId = entry.getKey();
            breakdown.add(new PersonBreakdownDto(userId, namesByUserId.get(userId),
                    entry.getValue()[0], entry.getValue()[1]));
        }
        return breakdown;
    }
}
