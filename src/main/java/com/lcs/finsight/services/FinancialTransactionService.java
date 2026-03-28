package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionRequestDto;
import com.lcs.finsight.exceptions.FinancialTransactionExceptions;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.specifications.FinancialTransactionSpecification;
import com.lcs.finsight.utils.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private static final Set<String> SORTABLE_FIELDS = Set.of("startDate", "endDate", "amount", "description");

    @Transactional(readOnly = true)
    public List<FinancialTransaction> findAllByUser(User user) {
        return financialTransactionRepository.findAllByUser(user);
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransaction> findAllByUserPaged(FinancialTransactionFilterDto filter, User user) {
        PageRequest pageable = filter.toPageable(SORTABLE_FIELDS);

        Specification<FinancialTransaction> spec = Specification.allOf(
                FinancialTransactionSpecification.belongsToUser(user),
                FinancialTransactionSpecification.typeEquals(filter.getType()),
                FinancialTransactionSpecification.categoryEquals(filter.getCategoryId()),
                FinancialTransactionSpecification.descriptionContains(filter.getDescription()),
                FinancialTransactionSpecification.startDateFrom(filter.getStartDateFrom()),
                FinancialTransactionSpecification.startDateTo(filter.getStartDateTo()),
                FinancialTransactionSpecification.amountMin(filter.getAmountMin()),
                FinancialTransactionSpecification.amountMax(filter.getAmountMax()));

        return financialTransactionRepository.findAll(spec, pageable);
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

    @Transactional
    public int importFromNubankCsv(MultipartFile file, User user) {
        try {
            List<String> lines = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
            ).lines().toList();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            List<FinancialTransaction> transactions = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isBlank()) continue;

                String[] parts = line.split(",", 4);
                if (parts.length < 4) continue;

                LocalDate date = LocalDate.parse(parts[0].trim(), formatter);
                BigDecimal rawAmount = new BigDecimal(parts[1].trim());
                String description = parts[3].trim();

                FinancialTransaction t = new FinancialTransaction();
                t.setUser(user);
                t.setStartDate(date);
                t.setAmount(rawAmount.abs());
                t.setType(rawAmount.compareTo(BigDecimal.ZERO) >= 0
                        ? FinancialTransactionType.CREDIT
                        : FinancialTransactionType.DEBIT);
                t.setDescription(description);

                transactions.add(t);
            }

            financialTransactionRepository.saveAll(transactions);
            return transactions.size();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar o arquivo CSV.", e);
        }
    }
}
