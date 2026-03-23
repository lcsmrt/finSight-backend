package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionRequestDto;
import com.lcs.finsight.exceptions.FinancialTransactionExceptions;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.utils.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialTransactionCategoryService financialTransactionCategoryService;
    private final DateUtils dateUtils;

    public FinancialTransactionService(
            FinancialTransactionRepository financialTransactionRepository,
            FinancialTransactionCategoryService financialTransactionCategoryService,
            DateUtils dateUtils
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionCategoryService = financialTransactionCategoryService;
        this.dateUtils = dateUtils;
    }

    @Transactional(readOnly = true)
    public FinancialTransaction findById(Long id, User user) {
        FinancialTransaction transaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new FinancialTransactionExceptions.FinancialTransactionNotFoundException(id));

        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new FinancialTransactionExceptions.FinancialTransactionNotFoundException(id);
        }

        return transaction;
    }

    @Transactional(readOnly = true)
    public List<FinancialTransaction> findAllByUser(User user) {
        return financialTransactionRepository.findAllByUser(user);
    }

    @Transactional
    public FinancialTransaction create(FinancialTransactionRequestDto dto, User user) {
        dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());

        FinancialTransaction financialTransaction = new FinancialTransaction();
        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), user)
                : null;

        financialTransaction.setUser(user);
        financialTransaction.setCategory(category);
        financialTransaction.setType(dto.getType());
        financialTransaction.setAmount(dto.getAmount());
        financialTransaction.setDescription(dto.getDescription());
        financialTransaction.setFrequency(dto.getFrequency());
        financialTransaction.setParcelsNumber(dto.getParcelsNumber());
        financialTransaction.setStartDate(dto.getStartDate());
        financialTransaction.setEndDate(dto.getEndDate());

        return financialTransactionRepository.save(financialTransaction);
    }

    @Transactional
    public FinancialTransaction update(Long id, FinancialTransactionRequestDto dto, User user) {
        dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());

        FinancialTransaction existingTransaction = findById(id, user);
        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), user)
                : null;

        existingTransaction.setCategory(category);
        existingTransaction.setType(dto.getType());
        existingTransaction.setAmount(dto.getAmount());
        existingTransaction.setDescription(dto.getDescription());
        existingTransaction.setFrequency(dto.getFrequency());
        existingTransaction.setParcelsNumber(dto.getParcelsNumber());
        existingTransaction.setStartDate(dto.getStartDate());
        existingTransaction.setEndDate(dto.getEndDate());

        return financialTransactionRepository.save(existingTransaction);
    }

    @Transactional
    public void delete(Long id, User user) {
        FinancialTransaction transaction = findById(id, user);
        financialTransactionRepository.delete(transaction);
    }
}
