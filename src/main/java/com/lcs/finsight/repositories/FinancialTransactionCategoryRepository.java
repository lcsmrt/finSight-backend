package com.lcs.finsight.repositories;

import com.lcs.finsight.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lcs.finsight.models.FinancialTransactionCategory;

import java.util.List;

@Repository
public interface FinancialTransactionCategoryRepository extends JpaRepository<FinancialTransactionCategory, Long> {
    List<FinancialTransactionCategory> findAllByUser(User user);
}
