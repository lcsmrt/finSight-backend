package com.lcs.finsight.repositories;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long>, JpaSpecificationExecutor<FinancialTransaction> {

    List<FinancialTransaction> findAllByPlan(Plan plan);

    List<FinancialTransaction> findAllByPlanAndSeriesId(Plan plan, String seriesId);

    @Query("SELECT ft.externalId FROM FinancialTransaction ft WHERE ft.plan = :plan AND ft.externalId IN :externalIds")
    Set<String> findExistingExternalIds(@Param("plan") Plan plan, @Param("externalIds") Collection<String> externalIds);

    // Participant-based aggregation: sums share_amount via the participants join, with an optional
    // memberId (null → whole plan). Under the SPLIT-01 invariant (every tx has >=1 participation whose
    // shares sum to amount) the unfiltered totals are identical to summing ft.amount (AGG-02).
    @Query("SELECT COALESCE(SUM(tp.shareAmount), 0) FROM FinancialTransaction ft JOIN ft.participants tp " +
           "WHERE ft.plan = :plan AND ft.type = :type " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "AND (:memberId IS NULL OR tp.member.id = :memberId)")
    BigDecimal sumByPlanAndTypeAndDateRange(
            @Param("plan") Plan plan,
            @Param("type") FinancialTransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("memberId") Long memberId);

    @Query("SELECT ft.category.id, ft.category.description, COALESCE(SUM(tp.shareAmount), 0), ft.category.spendingLimit " +
           "FROM FinancialTransaction ft JOIN ft.participants tp " +
           "WHERE ft.plan = :plan AND ft.type = :type AND ft.category IS NOT NULL " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "AND (:memberId IS NULL OR tp.member.id = :memberId) " +
           "GROUP BY ft.category.id, ft.category.description, ft.category.spendingLimit " +
           "ORDER BY SUM(tp.shareAmount) DESC")
    List<Object[]> findCategoryBreakdown(
            @Param("plan") Plan plan,
            @Param("type") FinancialTransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("memberId") Long memberId);

    @Query("SELECT ft.category.id, COALESCE(SUM(it.amount), 0) " +
           "FROM FinancialTransaction ft JOIN ft.items it " +
           "WHERE ft.plan = :plan AND ft.type = :type AND ft.category IS NOT NULL AND it.category IS NOT NULL " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "GROUP BY ft.category.id")
    List<Object[]> findCategorizedItemSumsByParentCategory(
            @Param("plan") Plan plan,
            @Param("type") FinancialTransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT it.category.id, it.category.description, it.category.spendingLimit, COALESCE(SUM(it.amount), 0) " +
           "FROM FinancialTransaction ft JOIN ft.items it " +
           "WHERE ft.plan = :plan AND ft.type = :type AND it.category IS NOT NULL " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "GROUP BY it.category.id, it.category.description, it.category.spendingLimit")
    List<Object[]> findCategorizedItemSumsByItemCategory(
            @Param("plan") Plan plan,
            @Param("type") FinancialTransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT year(ft.startDate), month(ft.startDate), ft.type, COALESCE(SUM(tp.shareAmount), 0) " +
           "FROM FinancialTransaction ft JOIN ft.participants tp " +
           "WHERE ft.plan = :plan " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "AND (:memberId IS NULL OR tp.member.id = :memberId) " +
           "GROUP BY year(ft.startDate), month(ft.startDate), ft.type " +
           "ORDER BY year(ft.startDate), month(ft.startDate)")
    List<Object[]> findMonthlyTrend(
            @Param("plan") Plan plan,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("memberId") Long memberId);

    @Query("SELECT tp.member.id, tp.member.name, ft.type, COALESCE(SUM(tp.shareAmount), 0) " +
           "FROM FinancialTransaction ft JOIN ft.participants tp " +
           "WHERE ft.plan = :plan " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "AND (:memberId IS NULL OR tp.member.id = :memberId) " +
           "GROUP BY tp.member.id, tp.member.name, ft.type")
    List<Object[]> findPersonBreakdown(
            @Param("plan") Plan plan,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("memberId") Long memberId);
}
