package com.lcs.finsight.specifications;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.TransactionParticipant;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FinancialTransactionSpecification {

    public static Specification<FinancialTransaction> belongsToPlan(Plan plan) {
        return (root, query, cb) -> cb.equal(root.get("plan"), plan);
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

    /**
     * Restrict to transactions where the given user is a participant. Implemented as an EXISTS
     * subquery (rather than a join) so it never multiplies rows and the paginated count stays correct.
     */
    public static Specification<FinancialTransaction> hasParticipant(Long memberId) {
        if (memberId == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<TransactionParticipant> tp = sub.from(TransactionParticipant.class);
            sub.select(tp.get("id"));
            sub.where(
                    cb.equal(tp.get("transaction"), root),
                    cb.equal(tp.get("member").get("id"), memberId));
            return cb.exists(sub);
        };
    }

    /**
     * Search matches EITHER the transaction description OR a participant member's name
     * (case-insensitive substring). The name match is an EXISTS subquery so a row matching on both
     * still appears exactly once — no join-induced duplicates.
     */
    public static Specification<FinancialTransaction> matchesSearchTerm(String term) {
        if (term == null || term.isBlank()) {
            return (root, query, cb) -> null;
        }
        String pattern = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate descriptionMatch = cb.like(cb.lower(root.get("description")), pattern);

            Subquery<Long> sub = query.subquery(Long.class);
            Root<TransactionParticipant> tp = sub.from(TransactionParticipant.class);
            sub.select(tp.get("id"));
            sub.where(
                    cb.equal(tp.get("transaction"), root),
                    cb.like(cb.lower(tp.get("member").get("name")), pattern));

            return cb.or(descriptionMatch, cb.exists(sub));
        };
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

    /**
     * Order by category name (the category's {@code description} field). Uses an explicit LEFT JOIN so
     * uncategorized rows are kept, and a null-rank order key so they always sort last in both
     * directions. This spec owns the ordering — the caller passes an unsorted Pageable for this key.
     */
    public static Specification<FinancialTransaction> orderByCategoryName(Sort.Direction direction) {
        return (root, query, cb) -> {
            if (isCountQuery(query.getResultType())) {
                return null;
            }
            Join<Object, Object> category = root.join("category", JoinType.LEFT);
            Expression<Integer> nullRank = cb.<Integer>selectCase()
                    .when(cb.isNull(category.get("id")), 1)
                    .otherwise(0)
                    .as(Integer.class);
            Expression<String> name = cb.lower(category.get("description"));
            Order nameOrder = direction.isAscending() ? cb.asc(name) : cb.desc(name);
            query.orderBy(cb.asc(nullRank), nameOrder);
            return null;
        };
    }

    /**
     * Order each transaction by the name of its largest-share participant, ties broken deterministically
     * by name (so paging is stable). Implemented as a correlated subquery selecting the least member name
     * among participants whose share equals the transaction's maximum share. This spec owns the ordering —
     * the caller passes an unsorted Pageable for this key.
     */
    public static Specification<FinancialTransaction> orderByLargestShareParticipant(Sort.Direction direction) {
        return (root, query, cb) -> {
            if (isCountQuery(query.getResultType())) {
                return null;
            }
            Subquery<String> nameSub = query.subquery(String.class);
            Root<TransactionParticipant> tp = nameSub.from(TransactionParticipant.class);

            Subquery<BigDecimal> maxShareSub = nameSub.subquery(BigDecimal.class);
            Root<TransactionParticipant> tpMax = maxShareSub.from(TransactionParticipant.class);
            Expression<BigDecimal> maxShare = tpMax.get("shareAmount");
            maxShareSub.select(cb.max(maxShare));
            maxShareSub.where(cb.equal(tpMax.get("transaction"), root));

            Expression<String> memberName = tp.get("member").get("name");
            Expression<BigDecimal> tpShare = tp.get("shareAmount");
            nameSub.select(cb.least(memberName));
            nameSub.where(
                    cb.equal(tp.get("transaction"), root),
                    cb.equal(tpShare, maxShareSub));

            query.orderBy(direction.isAscending() ? cb.asc(nameSub) : cb.desc(nameSub));
            return null;
        };
    }

    private static boolean isCountQuery(Class<?> resultType) {
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }
}
