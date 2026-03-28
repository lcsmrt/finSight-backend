package com.lcs.finsight.repositories;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.User;
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

    List<FinancialTransaction> findAllByUser(User user);

    @Query("SELECT ft.externalId FROM FinancialTransaction ft WHERE ft.user = :user AND ft.externalId IN :externalIds")
    Set<String> findExistingExternalIds(@Param("user") User user, @Param("externalIds") Collection<String> externalIds);

    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft " +
           "WHERE ft.user = :user AND ft.type = :type " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate")
    BigDecimal sumByUserAndTypeAndDateRange(
            @Param("user") User user,
            @Param("type") FinancialTransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT ft.category.description, COALESCE(SUM(ft.amount), 0), ft.category.spendingLimit " +
           "FROM FinancialTransaction ft " +
           "WHERE ft.user = :user AND ft.type = :type AND ft.category IS NOT NULL " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "GROUP BY ft.category.id, ft.category.description, ft.category.spendingLimit " +
           "ORDER BY SUM(ft.amount) DESC")
    List<Object[]> findCategoryBreakdown(
            @Param("user") User user,
            @Param("type") FinancialTransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT year(ft.startDate), month(ft.startDate), ft.type, COALESCE(SUM(ft.amount), 0) " +
           "FROM FinancialTransaction ft " +
           "WHERE ft.user = :user " +
           "AND ft.startDate >= :startDate AND ft.startDate <= :endDate " +
           "GROUP BY year(ft.startDate), month(ft.startDate), ft.type " +
           "ORDER BY year(ft.startDate), month(ft.startDate)")
    List<Object[]> findMonthlyTrend(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
