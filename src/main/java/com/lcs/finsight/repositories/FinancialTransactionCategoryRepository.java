package com.lcs.finsight.repositories;

import com.lcs.finsight.models.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.lcs.finsight.models.FinancialTransactionCategory;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialTransactionCategoryRepository extends JpaRepository<FinancialTransactionCategory, Long>, JpaSpecificationExecutor<FinancialTransactionCategory> {
    List<FinancialTransactionCategory> findAllByPlan(Plan plan);

    Optional<FinancialTransactionCategory> findByIdAndPlan(Long id, Plan plan);
}
