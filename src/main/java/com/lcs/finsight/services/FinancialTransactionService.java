package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionFilterDto;
import com.lcs.finsight.dtos.request.FinancialTransactionRequestDto;
import com.lcs.finsight.dtos.request.FinancialTransactionSeriesRequestDto;
import com.lcs.finsight.exceptions.FinancialTransactionExceptions;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.security.PlanAuthorization;
import com.lcs.finsight.security.PlanContext;
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
import java.util.UUID;

@Service
public class FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialTransactionCategoryService financialTransactionCategoryService;
    private final DateUtils dateUtils;
    private final RecurringTransactionGenerator recurringTransactionGenerator;
    private final PlanAuthorization planAuthorization;

    public FinancialTransactionService(
            FinancialTransactionRepository financialTransactionRepository,
            FinancialTransactionCategoryService financialTransactionCategoryService,
            DateUtils dateUtils,
            RecurringTransactionGenerator recurringTransactionGenerator,
            PlanAuthorization planAuthorization
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionCategoryService = financialTransactionCategoryService;
        this.dateUtils = dateUtils;
        this.recurringTransactionGenerator = recurringTransactionGenerator;
        this.planAuthorization = planAuthorization;
    }

    @Transactional(readOnly = true)
    public FinancialTransaction findById(Long id, PlanContext ctx) {
        FinancialTransaction transaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new FinancialTransactionExceptions.FinancialTransactionNotFoundException(id));

        if (!transaction.getPlan().getId().equals(ctx.getPlan().getId())) {
            throw new FinancialTransactionExceptions.FinancialTransactionNotFoundException(id);
        }

        return transaction;
    }

    private static final Set<String> SORTABLE_FIELDS = Set.of("startDate", "endDate", "amount", "description");

    @Transactional(readOnly = true)
    public List<FinancialTransaction> findAllByPlan(PlanContext ctx) {
        return financialTransactionRepository.findAllByPlan(ctx.getPlan());
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransaction> findAllByPlanPaged(FinancialTransactionFilterDto filter, PlanContext ctx) {
        PageRequest pageable = filter.toPageable(SORTABLE_FIELDS);

        Specification<FinancialTransaction> spec = Specification.allOf(
                FinancialTransactionSpecification.belongsToPlan(ctx.getPlan()),
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
    public FinancialTransaction create(FinancialTransactionRequestDto dto, PlanContext ctx) {
        planAuthorization.requireCanCreateTransaction(ctx.getRole());
        dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());

        FinancialTransaction financialTransaction = new FinancialTransaction();
        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), ctx)
                : null;

        if (category != null && category.getType() != dto.getType()) {
            throw new IllegalArgumentException("Category does not match the transaction type.");
        }

        financialTransaction.setPlan(ctx.getPlan());
        financialTransaction.setCreatedBy(ctx.getUser());
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
    public FinancialTransaction update(Long id, FinancialTransactionRequestDto dto, PlanContext ctx) {
        dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());

        FinancialTransaction existingTransaction = findById(id, ctx);
        planAuthorization.requireCanModifyTransaction(ctx.getRole(), existingTransaction.getCreatedBy(), ctx.getUser());

        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), ctx)
                : null;

        if (category != null && category.getType() != dto.getType()) {
            throw new IllegalArgumentException("Category does not match the transaction type.");
        }

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
    public void delete(Long id, PlanContext ctx) {
        FinancialTransaction transaction = findById(id, ctx);
        planAuthorization.requireCanModifyTransaction(ctx.getRole(), transaction.getCreatedBy(), ctx.getUser());
        financialTransactionRepository.delete(transaction);
    }

    @Transactional
    public List<FinancialTransaction> createSeries(FinancialTransactionSeriesRequestDto dto, PlanContext ctx) {
        planAuthorization.requireCanCreateTransaction(ctx.getRole());

        if (dto.getMode() == RecurrenceMode.INSTALLMENT) {
            if (dto.getParcelsNumber() == null) {
                throw new IllegalArgumentException("Parcels number is required for installment series.");
            }
            if (dto.getCurrentParcel() != null
                    && (dto.getCurrentParcel() < 1 || dto.getCurrentParcel() > dto.getParcelsNumber())) {
                throw new IllegalArgumentException("Current parcel must be between 1 and the total number of parcels.");
            }
        } else if (dto.getMode() == RecurrenceMode.RECURRING) {
            if (dto.getInterval() == null) {
                throw new IllegalArgumentException("Interval is required for recurring series.");
            }
            if (dto.getEndDate() == null) {
                throw new IllegalArgumentException("End date is required for recurring series.");
            }
            dateUtils.checkIfStartDateIsBeforeEndDate(dto.getStartDate(), dto.getEndDate());
        }

        FinancialTransactionCategory category = dto.getCategoryId() != null
                ? financialTransactionCategoryService.findById(dto.getCategoryId(), ctx)
                : null;

        if (category != null && category.getType() != dto.getType()) {
            throw new IllegalArgumentException("Category does not match the transaction type.");
        }

        String seriesId = UUID.randomUUID().toString();
        List<FinancialTransaction> occurrences = recurringTransactionGenerator.generate(
                dto, ctx.getPlan(), ctx.getUser(), category, seriesId);

        return financialTransactionRepository.saveAll(occurrences);
    }

    @Transactional
    public void deleteSeries(String seriesId, PlanContext ctx) {
        List<FinancialTransaction> occurrences = financialTransactionRepository.findAllByPlanAndSeriesId(ctx.getPlan(), seriesId);

        if (occurrences.isEmpty()) {
            throw new FinancialTransactionExceptions.FinancialTransactionSeriesNotFoundException(seriesId);
        }

        for (FinancialTransaction occurrence : occurrences) {
            planAuthorization.requireCanModifyTransaction(ctx.getRole(), occurrence.getCreatedBy(), ctx.getUser());
        }

        financialTransactionRepository.deleteAll(occurrences);
    }

    @Transactional
    public int importFromNubankCsv(MultipartFile file, PlanContext ctx) {
        planAuthorization.requireCanCreateTransaction(ctx.getRole());
        try {
            List<String> lines = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
            ).lines().toList();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            List<FinancialTransaction> transactions = new ArrayList<>();

            record ParsedRow(String externalId, LocalDate date, BigDecimal rawAmount, String description) {}
            List<ParsedRow> parsed = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isBlank()) continue;

                String[] parts = line.split(",", 4);
                if (parts.length < 4) continue;

                parsed.add(new ParsedRow(
                        parts[2].trim(),
                        LocalDate.parse(parts[0].trim(), formatter),
                        new BigDecimal(parts[1].trim()),
                        parts[3].trim()
                ));
            }

            Set<String> existingIds = financialTransactionRepository.findExistingExternalIds(
                    ctx.getPlan(), parsed.stream().map(ParsedRow::externalId).toList());

            for (ParsedRow row : parsed) {
                if (existingIds.contains(row.externalId())) continue;

                FinancialTransaction t = new FinancialTransaction();
                t.setPlan(ctx.getPlan());
                t.setCreatedBy(ctx.getUser());
                t.setExternalId(row.externalId());
                t.setStartDate(row.date());
                t.setAmount(row.rawAmount().abs());
                t.setType(row.rawAmount().compareTo(BigDecimal.ZERO) >= 0
                        ? FinancialTransactionType.CREDIT
                        : FinancialTransactionType.DEBIT);
                t.setDescription(row.description());

                transactions.add(t);
            }

            financialTransactionRepository.saveAll(transactions);
            return transactions.size();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar o arquivo CSV.", e);
        }
    }
}
