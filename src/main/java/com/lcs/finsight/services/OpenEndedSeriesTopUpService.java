package com.lcs.finsight.services;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.repositories.RecurrenceDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lazy, on-read "rolling top-up" for open-ended (mode = RECURRING, endDate = null) series (D1/D2,
 * see {@code .specs/features/recurrence-model-v2/design.md}). Each due definition is materialized
 * from its {@code generatedThrough} watermark up through the rolling horizon ({@code today + 12mo}),
 * reflecting the definition's *current* amount/category/split — consistent with a series-edit
 * scope ALL.
 *
 * <p>Bounded series ({@code endDate} set) are excluded entirely by the due-query and are never
 * touched by this service.
 *
 * <p><b>Idempotency</b>: the due-query locks each candidate definition row
 * ({@code PESSIMISTIC_WRITE}), so a concurrent top-up serializes and observes the already-advanced
 * watermark. As a second line of defense (e.g. the watermark being reset backward), generated
 * candidates are also filtered against the series' already-persisted occurrence dates before
 * saving, so no duplicate row is ever written regardless of how top-up is re-triggered.
 */
@Service
public class OpenEndedSeriesTopUpService {

    /** Rolling look-ahead horizon (in months), mirrors {@code RecurringTransactionGenerator}'s D2 horizon. */
    private static final int HORIZON_MONTHS = 12;

    private final RecurrenceDefinitionRepository recurrenceDefinitionRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final RecurringTransactionGenerator recurringTransactionGenerator;

    public OpenEndedSeriesTopUpService(RecurrenceDefinitionRepository recurrenceDefinitionRepository,
                                        FinancialTransactionRepository financialTransactionRepository,
                                        RecurringTransactionGenerator recurringTransactionGenerator) {
        this.recurrenceDefinitionRepository = recurrenceDefinitionRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.recurringTransactionGenerator = recurringTransactionGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void topUp(Plan plan, LocalDate today) {
        LocalDate horizonCap = today.plusMonths(HORIZON_MONTHS);

        List<RecurrenceDefinition> due = recurrenceDefinitionRepository.findOpenEndedDue(plan, horizonCap);
        for (RecurrenceDefinition definition : due) {
            topUpOne(definition, horizonCap);
        }
    }

    private void topUpOne(RecurrenceDefinition definition, LocalDate horizonCap) {
        LocalDate afterExclusive = definition.getGeneratedThrough() != null
                ? definition.getGeneratedThrough()
                : definition.getStartDate().minusMonths(1);

        List<ResolvedParticipant> shares = definition.getParticipants().stream()
                .map(participant -> new ResolvedParticipant(participant.getMember(), participant.getShareAmount()))
                .toList();

        List<FinancialTransaction> candidates = recurringTransactionGenerator.generateForwardWindow(
                definition, afterExclusive, horizonCap, shares);

        if (candidates.isEmpty()) {
            return;
        }

        Set<LocalDate> existingDates = financialTransactionRepository
                .findAllByPlanAndSeriesId(definition.getPlan(), definition.getSeriesId())
                .stream()
                .map(FinancialTransaction::getStartDate)
                .collect(Collectors.toSet());

        List<FinancialTransaction> toPersist = candidates.stream()
                .filter(occurrence -> !existingDates.contains(occurrence.getStartDate()))
                .toList();

        if (!toPersist.isEmpty()) {
            for (FinancialTransaction occurrence : toPersist) {
                occurrence.setRecurrenceDefinition(definition);
            }
            financialTransactionRepository.saveAll(toPersist);
        }

        LocalDate lastGenerated = candidates.stream()
                .map(FinancialTransaction::getStartDate)
                .max(Comparator.naturalOrder())
                .orElse(definition.getGeneratedThrough());

        definition.setGeneratedThrough(lastGenerated);
        recurrenceDefinitionRepository.save(definition);
    }
}
