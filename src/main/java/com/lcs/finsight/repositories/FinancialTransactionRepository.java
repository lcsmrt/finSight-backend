package com.lcs.finsight.repositories;

import com.lcs.finsight.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lcs.finsight.models.FinancialTransaction;

import java.util.List;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    List<FinancialTransaction> findAllByUser(User user);
}
