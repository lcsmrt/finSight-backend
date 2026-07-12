package com.lcs.finsight.specifications;

import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import org.springframework.data.jpa.domain.Specification;

public class FinancialTransactionCategorySpecification {

    public static Specification<FinancialTransactionCategory> belongsToPlan(Plan plan) {
        return (root, query, cb) -> cb.equal(root.get("plan"), plan);
    }

    public static Specification<FinancialTransactionCategory> descriptionContains(String description) {
        if (description == null || description.isBlank()) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), "%" + description.toLowerCase() + "%");
    }

    public static Specification<FinancialTransactionCategory> typeEquals(FinancialTransactionType type) {
        if (type == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }
}
