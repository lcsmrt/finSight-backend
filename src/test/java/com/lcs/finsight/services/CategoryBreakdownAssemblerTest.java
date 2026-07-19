package com.lcs.finsight.services;

import com.lcs.finsight.dtos.response.CategoryBreakdownDto;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryBreakdownAssemblerTest {

    private final CategoryBreakdownAssembler assembler = new CategoryBreakdownAssembler();

    @Test
    void workedExampleSplitsGroceryTransactionAcrossItemCategories() {
        List<Object[]> rowsA = List.<Object[]>of(
                new Object[]{1L, "Groceries", new BigDecimal("150"), new BigDecimal("500")});
        List<Object[]> rowsB = List.<Object[]>of(
                new Object[]{1L, new BigDecimal("130")});
        List<Object[]> rowsI = List.<Object[]>of(
                new Object[]{2L, "Food", null, new BigDecimal("90")},
                new Object[]{3L, "Cleaning", null, new BigDecimal("40")});

        List<CategoryBreakdownDto> result = assembler.assemble(rowsA, rowsB, rowsI, List.of());

        assertThat(result).hasSize(3);
        assertThat(byName(result, "Groceries").getSpent()).isEqualByComparingTo("20");
        assertThat(byName(result, "Food").getSpent()).isEqualByComparingTo("90");
        assertThat(byName(result, "Cleaning").getSpent()).isEqualByComparingTo("40");
        assertThat(result).extracting(CategoryBreakdownDto::getCategoryName)
                .containsExactly("Food", "Cleaning", "Groceries");
    }

    @Test
    void nonItemizedTransactionSpendEqualsRawAmount() {
        List<Object[]> rowsA = List.<Object[]>of(
                new Object[]{10L, "Utilities", new BigDecimal("75"), null});

        List<CategoryBreakdownDto> result = assembler.assemble(rowsA, List.of(), List.of(), List.of());

        assertThat(result).hasSize(1);
        CategoryBreakdownDto utilities = result.get(0);
        assertThat(utilities.getCategoryName()).isEqualTo("Utilities");
        assertThat(utilities.getSpent()).isEqualByComparingTo("75");
    }

    @Test
    void categoryPresentOnlyViaItemsStillAppearsWithItsItemSum() {
        List<Object[]> rowsI = List.<Object[]>of(
                new Object[]{5L, "Subscriptions", new BigDecimal("100"), new BigDecimal("25")});

        List<CategoryBreakdownDto> result = assembler.assemble(List.of(), List.of(), rowsI, List.of());

        assertThat(result).hasSize(1);
        CategoryBreakdownDto subscriptions = result.get(0);
        assertThat(subscriptions.getCategoryName()).isEqualTo("Subscriptions");
        assertThat(subscriptions.getSpent()).isEqualByComparingTo("25");
        assertThat(subscriptions.getLimit()).isEqualByComparingTo("100");
    }

    @Test
    void fullyItemizedTransactionLeavesParentCategoryNetZero() {
        List<Object[]> rowsA = List.<Object[]>of(
                new Object[]{6L, "Entertainment", new BigDecimal("200"), null});
        List<Object[]> rowsB = List.<Object[]>of(
                new Object[]{6L, new BigDecimal("200")});
        List<Object[]> rowsI = List.<Object[]>of(
                new Object[]{7L, "Movies", null, new BigDecimal("120")},
                new Object[]{8L, "Snacks", null, new BigDecimal("80")});

        List<CategoryBreakdownDto> result = assembler.assemble(rowsA, rowsB, rowsI, List.of());

        assertThat(result).hasSize(3);
        assertThat(byName(result, "Entertainment").getSpent()).isEqualByComparingTo("0");
        assertThat(byName(result, "Movies").getSpent()).isEqualByComparingTo("120");
        assertThat(byName(result, "Snacks").getSpent()).isEqualByComparingTo("80");
    }

    @Test
    void multipleItemRowsForSameCategoryAreSummedNotOverwritten() {
        List<Object[]> rowsI = List.<Object[]>of(
                new Object[]{11L, "Fuel", null, new BigDecimal("30")},
                new Object[]{11L, "Fuel", null, new BigDecimal("45")});

        List<CategoryBreakdownDto> result = assembler.assemble(List.of(), List.of(), rowsI, List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSpent()).isEqualByComparingTo("75");
    }

    @Test
    void zeroSpendCategoryWithLimitIsBackfilledWhenAbsentFromAllRows() {
        FinancialTransactionCategory emergencyFund = new FinancialTransactionCategory();
        emergencyFund.setType(FinancialTransactionType.DEBIT);
        emergencyFund.setDescription("Emergency Fund");
        emergencyFund.setSpendingLimit(new BigDecimal("1000"));

        FinancialTransactionCategory noLimit = new FinancialTransactionCategory();
        noLimit.setType(FinancialTransactionType.DEBIT);
        noLimit.setDescription("No Limit Category");
        noLimit.setSpendingLimit(null);

        FinancialTransactionCategory income = new FinancialTransactionCategory();
        income.setType(FinancialTransactionType.CREDIT);
        income.setDescription("Salary");
        income.setSpendingLimit(new BigDecimal("2000"));

        List<CategoryBreakdownDto> result = assembler.assemble(
                List.of(), List.of(), List.of(), List.of(emergencyFund, noLimit, income));

        assertThat(result).hasSize(1);
        CategoryBreakdownDto backfilled = result.get(0);
        assertThat(backfilled.getCategoryName()).isEqualTo("Emergency Fund");
        assertThat(backfilled.getSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(backfilled.getLimit()).isEqualByComparingTo("1000");
    }

    private static CategoryBreakdownDto byName(List<CategoryBreakdownDto> result, String name) {
        return result.stream()
                .filter(dto -> dto.getCategoryName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No category named " + name + " in result: " + result));
    }
}
