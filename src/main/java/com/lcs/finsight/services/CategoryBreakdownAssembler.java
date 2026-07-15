package com.lcs.finsight.services;

import com.lcs.finsight.dtos.response.CategoryBreakdownDto;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure assembler for the category spending breakdown, now item-aware.
 * <p>
 * A transaction's amount is sliced across category buckets exactly once. For category {@code C}:
 * <pre>spent[C] = A[C] - B[C] + I[C]</pre>
 * where {@code A[C]} is the sum of whole-transaction amounts whose parent category is {@code C},
 * {@code B[C]} is the sum of categorized-item amounts belonging to transactions whose parent
 * category is {@code C} (subtracted out, since it's now attributed to the item's own category),
 * and {@code I[C]} is the sum of categorized-item amounts whose own category is {@code C}.
 * <p>
 * No Spring context/dependencies are required; all inputs are plain query result rows.
 */
@Component
public class CategoryBreakdownAssembler {

    private record CategoryInfo(String name, BigDecimal limit) {}

    /**
     * Assembles the category breakdown from the three raw query row sets plus the zero-spend
     * back-fill candidates.
     *
     * @param rowsA           query A rows: {@code [Long categoryId, String description, BigDecimal spent, BigDecimal spendingLimit]}
     * @param rowsB           query B rows: {@code [Long categoryId, BigDecimal sum]}
     * @param rowsI           query I rows: {@code [Long categoryId, String description, BigDecimal spendingLimit, BigDecimal sum]}
     * @param limitCategories all plan categories (unfiltered by type); filtered internally to
     *                        {@link FinancialTransactionType#DEBIT} categories with a non-null spending limit,
     *                        used to back-fill zero-spend rows that never produced a query row at all
     * @return the assembled breakdown, sorted by {@code spent} descending
     */
    public List<CategoryBreakdownDto> assemble(List<Object[]> rowsA,
                                                List<Object[]> rowsB,
                                                List<Object[]> rowsI,
                                                List<FinancialTransactionCategory> limitCategories) {
        Map<Long, CategoryInfo> infoById = new LinkedHashMap<>();
        Map<Long, BigDecimal> aById = new LinkedHashMap<>();
        Map<Long, BigDecimal> bById = new LinkedHashMap<>();
        Map<Long, BigDecimal> iById = new LinkedHashMap<>();

        for (Object[] row : rowsA) {
            Long categoryId = (Long) row[0];
            String description = (String) row[1];
            BigDecimal spent = (BigDecimal) row[2];
            BigDecimal limit = (BigDecimal) row[3];
            infoById.put(categoryId, new CategoryInfo(description, limit));
            aById.merge(categoryId, spent == null ? BigDecimal.ZERO : spent, BigDecimal::add);
        }

        for (Object[] row : rowsB) {
            Long categoryId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            bById.merge(categoryId, sum == null ? BigDecimal.ZERO : sum, BigDecimal::add);
        }

        for (Object[] row : rowsI) {
            Long categoryId = (Long) row[0];
            String description = (String) row[1];
            BigDecimal limit = (BigDecimal) row[2];
            BigDecimal sum = (BigDecimal) row[3];
            infoById.putIfAbsent(categoryId, new CategoryInfo(description, limit));
            iById.merge(categoryId, sum == null ? BigDecimal.ZERO : sum, BigDecimal::add);
        }

        Map<Long, CategoryBreakdownDto> resultById = new LinkedHashMap<>();
        for (Map.Entry<Long, CategoryInfo> entry : infoById.entrySet()) {
            Long categoryId = entry.getKey();
            CategoryInfo info = entry.getValue();
            BigDecimal a = aById.getOrDefault(categoryId, BigDecimal.ZERO);
            BigDecimal b = bById.getOrDefault(categoryId, BigDecimal.ZERO);
            BigDecimal i = iById.getOrDefault(categoryId, BigDecimal.ZERO);
            BigDecimal spent = a.subtract(b).add(i);
            resultById.put(categoryId, new CategoryBreakdownDto(info.name(), spent, info.limit()));
        }

        for (FinancialTransactionCategory category : limitCategories) {
            if (category.getType() == FinancialTransactionType.DEBIT
                    && category.getSpendingLimit() != null
                    && !resultById.containsKey(category.getId())) {
                resultById.put(category.getId(),
                        new CategoryBreakdownDto(category.getDescription(), BigDecimal.ZERO, category.getSpendingLimit()));
            }
        }

        List<CategoryBreakdownDto> result = new ArrayList<>(resultById.values());
        result.sort((left, right) -> right.getSpent().compareTo(left.getSpent()));
        return result;
    }
}
