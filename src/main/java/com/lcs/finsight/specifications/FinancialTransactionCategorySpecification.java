package com.lcs.finsight.specifications;

import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.User;
import org.springframework.data.jpa.domain.Specification;

public class FinancialTransactionCategorySpecification {

    public static Specification<FinancialTransactionCategory> belongsToUser(User user) {
        return (root, query, cb) -> cb.equal(root.get("user"), user);
    }

    public static Specification<FinancialTransactionCategory> descriptionContains(String description) {
        if (description == null || description.isBlank()) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), "%" + description.toLowerCase() + "%");
    }
}
