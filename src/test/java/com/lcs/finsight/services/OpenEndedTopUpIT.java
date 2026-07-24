package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceDefinitionParticipant;
import com.lcs.finsight.models.RecurrenceInterval;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.models.TransactionParticipant;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.FinancialTransactionRepository;
import com.lcs.finsight.repositories.RecurrenceDefinitionRepository;
import com.lcs.finsight.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves {@link OpenEndedSeriesTopUpService} (RMV2-06, D1/D2): lazily materializing missing
 * future months for open-ended (mode = RECURRING, endDate = null) series up to the rolling
 * 12-month horizon, idempotently, without ever touching bounded series.
 *
 * <p>Fixtures build {@link RecurrenceDefinition}/{@link FinancialTransaction} rows directly via
 * the repositories (bypassing {@code createSeries}, owned by a sibling task) so this suite is
 * self-contained and exercises the top-up service and its locked due-query in isolation.
 *
 * <p>"Today" is always derived from an injected {@link Clock} (never the ambient
 * {@code LocalDate.now()}), so the rolling horizon is deterministic and the watermark math is
 * exact. All fixture dates use day-of-month 1 so the monthly stepping in the generator always
 * lands exactly on the horizon date, keeping the arithmetic in these assertions unambiguous.
 */
class OpenEndedTopUpIT extends AbstractIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final LocalDate HORIZON_CAP = TODAY.plusMonths(12);

    @Autowired
    private RecurrenceDefinitionRepository recurrenceDefinitionRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private OpenEndedSeriesTopUpService topUpService;

    @Test
    void topUpMaterializesMissingMonthsUpToHorizonAndAdvancesWatermarkUsingCurrentDefinitionValues() {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = TODAY.minusMonths(6);
        LocalDate generatedThrough = TODAY.minusMonths(2);

        RecurrenceDefinition definition = anOpenEndedDefinition(plan, owner, start, "Gym", new BigDecimal("50.00"));
        seedOccurrences(definition, start, generatedThrough);
        definition.setGeneratedThrough(generatedThrough);
        recurrenceDefinitionRepository.save(definition);

        // Simulate the definition having been edited (scope ALL) after the last materialization,
        // before the rolling top-up runs: newly materialized months must reflect this, not the
        // stale amount the seeded occurrences were stamped with.
        definition.setAmount(new BigDecimal("75.00"));
        recurrenceDefinitionRepository.save(definition);

        topUpService.topUp(plan, TODAY);

        List<FinancialTransaction> occurrences = financialTransactionRepository
                .findAllByPlanAndSeriesId(plan, definition.getSeriesId());

        long expectedTotal = ChronoUnit.MONTHS.between(start, HORIZON_CAP) + 1;
        assertThat(occurrences).hasSize((int) expectedTotal);

        RecurrenceDefinition reloaded = recurrenceDefinitionRepository.findById(definition.getId()).orElseThrow();
        assertThat(reloaded.getGeneratedThrough()).isEqualTo(HORIZON_CAP);

        assertThat(findByDate(occurrences, generatedThrough).getAmount()).isEqualByComparingTo("50.00");
        assertThat(findByDate(occurrences, generatedThrough.plusMonths(1)).getAmount()).isEqualByComparingTo("75.00");
        assertThat(findByDate(occurrences, HORIZON_CAP).getAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void topUpIsNoOpWhenWatermarkAlreadyMeetsHorizon() {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = TODAY.minusMonths(1);

        RecurrenceDefinition definition = anOpenEndedDefinition(plan, owner, start, "Streaming", new BigDecimal("20.00"));
        definition.setGeneratedThrough(HORIZON_CAP);
        recurrenceDefinitionRepository.save(definition);

        topUpService.topUp(plan, TODAY);

        List<FinancialTransaction> occurrences = financialTransactionRepository
                .findAllByPlanAndSeriesId(plan, definition.getSeriesId());
        assertThat(occurrences).isEmpty();

        RecurrenceDefinition reloaded = recurrenceDefinitionRepository.findById(definition.getId()).orElseThrow();
        assertThat(reloaded.getGeneratedThrough()).isEqualTo(HORIZON_CAP);
    }

    @Test
    void firingTopUpTwiceProducesNoDuplicateOccurrences() {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = TODAY.minusMonths(2);

        RecurrenceDefinition definition = anOpenEndedDefinition(plan, owner, start, "Internet", new BigDecimal("100.00"));

        topUpService.topUp(plan, TODAY);
        List<FinancialTransaction> firstPass = financialTransactionRepository
                .findAllByPlanAndSeriesId(plan, definition.getSeriesId());
        long expectedTotal = ChronoUnit.MONTHS.between(start, HORIZON_CAP) + 1;
        assertThat(firstPass).hasSize((int) expectedTotal);

        topUpService.topUp(plan, TODAY);
        List<FinancialTransaction> secondPass = financialTransactionRepository
                .findAllByPlanAndSeriesId(plan, definition.getSeriesId());
        assertThat(secondPass).hasSize((int) expectedTotal);

        RecurrenceDefinition reloaded = recurrenceDefinitionRepository.findById(definition.getId()).orElseThrow();
        assertThat(reloaded.getGeneratedThrough()).isEqualTo(HORIZON_CAP);
    }

    @Test
    void resettingWatermarkBackwardThenFiringSelfHealsWithoutDuplicating() {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = TODAY.minusMonths(2);

        RecurrenceDefinition definition = anOpenEndedDefinition(plan, owner, start, "Cloud storage", new BigDecimal("15.00"));

        topUpService.topUp(plan, TODAY);
        long expectedTotal = ChronoUnit.MONTHS.between(start, HORIZON_CAP) + 1;
        assertThat(financialTransactionRepository.findAllByPlanAndSeriesId(plan, definition.getSeriesId()))
                .hasSize((int) expectedTotal);

        // Simulate the watermark drifting backward (e.g. a bug, or a race with a bounding edit that
        // was later rolled back) — top-up must reconcile against what's already persisted rather
        // than blindly regenerating from the stale watermark.
        RecurrenceDefinition reloaded = recurrenceDefinitionRepository.findById(definition.getId()).orElseThrow();
        reloaded.setGeneratedThrough(start);
        recurrenceDefinitionRepository.save(reloaded);

        topUpService.topUp(plan, TODAY);

        List<FinancialTransaction> afterSecondFire = financialTransactionRepository
                .findAllByPlanAndSeriesId(plan, definition.getSeriesId());
        assertThat(afterSecondFire).hasSize((int) expectedTotal);

        RecurrenceDefinition healed = recurrenceDefinitionRepository.findById(definition.getId()).orElseThrow();
        assertThat(healed.getGeneratedThrough()).isEqualTo(HORIZON_CAP);
    }

    @Test
    void boundedSeriesAreNeverSelectedOrToppedUp() {
        User owner = fixtures.aUser();
        Plan plan = fixtures.aPlan(owner);
        LocalDate start = TODAY.minusMonths(3);
        LocalDate end = TODAY.plusMonths(2);

        RecurrenceDefinition definition = aBoundedDefinition(plan, owner, start, end, "Car loan", new BigDecimal("400.00"));

        topUpService.topUp(plan, TODAY);

        List<FinancialTransaction> occurrences = financialTransactionRepository
                .findAllByPlanAndSeriesId(plan, definition.getSeriesId());
        assertThat(occurrences).isEmpty();

        RecurrenceDefinition reloaded = recurrenceDefinitionRepository.findById(definition.getId()).orElseThrow();
        assertThat(reloaded.getGeneratedThrough()).isNull();
        assertThat(reloaded.getEndDate()).isEqualTo(end);
    }

    private RecurrenceDefinition anOpenEndedDefinition(
            Plan plan, User owner, LocalDate start, String description, BigDecimal amount) {
        return aDefinition(plan, owner, start, null, description, amount);
    }

    private RecurrenceDefinition aBoundedDefinition(
            Plan plan, User owner, LocalDate start, LocalDate end, String description, BigDecimal amount) {
        return aDefinition(plan, owner, start, end, description, amount);
    }

    private RecurrenceDefinition aDefinition(
            Plan plan, User owner, LocalDate start, LocalDate end, String description, BigDecimal amount) {
        RecurrenceDefinition definition = new RecurrenceDefinition();
        definition.setPlan(plan);
        definition.setCreatedBy(owner);
        definition.setCategory(null);
        definition.setSeriesId(UUID.randomUUID().toString());
        definition.setType(FinancialTransactionType.DEBIT);
        definition.setAmount(amount);
        definition.setDescription(description);
        definition.setMode(RecurrenceMode.RECURRING);
        definition.setRecurrenceInterval(RecurrenceInterval.MONTHLY);
        definition.setStartDate(start);
        definition.setEndDate(end);
        definition.setSplitMode(SplitMode.EQUAL);

        RecurrenceDefinitionParticipant participant = new RecurrenceDefinitionParticipant();
        participant.setDefinition(definition);
        participant.setMember(owner);
        participant.setShareAmount(amount);
        definition.getParticipants().add(participant);

        return recurrenceDefinitionRepository.save(definition);
    }

    private void seedOccurrences(RecurrenceDefinition definition, LocalDate from, LocalDate throughInclusive) {
        List<FinancialTransaction> occurrences = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(throughInclusive); date = date.plusMonths(1)) {
            FinancialTransaction tx = new FinancialTransaction();
            tx.setPlan(definition.getPlan());
            tx.setCreatedBy(definition.getCreatedBy());
            tx.setCategory(definition.getCategory());
            tx.setType(definition.getType());
            tx.setAmount(definition.getAmount());
            tx.setDescription(definition.getDescription());
            tx.setStartDate(date);
            tx.setEndDate(null);
            tx.setSeriesId(definition.getSeriesId());
            tx.setSplitMode(definition.getSplitMode());
            tx.setRecurrenceDefinition(definition);

            TransactionParticipant participant = new TransactionParticipant();
            participant.setTransaction(tx);
            participant.setMember(definition.getCreatedBy());
            participant.setShareAmount(definition.getAmount());
            tx.getParticipants().add(participant);

            occurrences.add(tx);
        }
        financialTransactionRepository.saveAll(occurrences);
    }

    private FinancialTransaction findByDate(List<FinancialTransaction> occurrences, LocalDate date) {
        return occurrences.stream()
                .filter(occurrence -> occurrence.getStartDate().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No occurrence dated " + date + " in " + occurrences));
    }
}
