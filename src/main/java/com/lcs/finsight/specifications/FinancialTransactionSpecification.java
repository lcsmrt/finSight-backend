package com.lcs.finsight.specifications;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.User;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FinancialTransactionSpecification {

    public static Specification<FinancialTransaction> belongsToUser(User user) {
        return (root, query, cb) -> cb.equal(root.get("user"), user);
    }

    public static Specification<FinancialTransaction> typeEquals(FinancialTransactionType type) {
        if (type == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<FinancialTransaction> categoryEquals(Long categoryId) {
        if (categoryId == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<FinancialTransaction> descriptionContains(String description) {
        if (description == null || description.isBlank()) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), "%" + description.toLowerCase() + "%");
    }

    public static Specification<FinancialTransaction> startDateFrom(LocalDate date) {
        if (date == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startDate"), date);
    }

    public static Specification<FinancialTransaction> startDateTo(LocalDate date) {
        if (date == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), date);
    }

    public static Specification<FinancialTransaction> amountMin(BigDecimal amount) {
        if (amount == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), amount);
    }

    public static Specification<FinancialTransaction> amountMax(BigDecimal amount) {
        if (amount == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), amount);
    }
}
