package com.lcs.finsight.services;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SeriesEditScope;
import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.models.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeriesRegeneratorTest {

    // Fixed "today" so open-ended (null endDate) horizon assertions are deterministic regardless of
    // wall-clock time, mirroring RecurringTransactionGeneratorTest's FIXED_CLOCK.
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC);

    private final SeriesRegenerator regenerator = new SeriesRegenerator(FIXED_CLOCK);

    private final Plan plan = new Plan();
    private final User user = userWithId(1L);
    private final BigDecimal amount = new BigDecimal("300.00");
    private final List<ResolvedParticipant> shares = List.of(new ResolvedParticipant(user, amount));
    private static final String SERIES_ID = "test-series";

    private static User userWithId(Long id) {
        User u = new User();
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    private RecurrenceDefinition installmentDef(String description, BigDecimal amt, int firstParcel,
                                                  int parcelsNumber, LocalDate startDate) {
        RecurrenceDefinition def = new RecurrenceDefinition();
        def.setPlan(plan);
        def.setCreatedBy(user);
        def.setCategory(null);
        def.setSeriesId(SERIES_ID);
        def.setType(FinancialTransactionType.DEBIT);
        def.setAmount(amt);
        def.setDescription(description);
        def.setMode(RecurrenceMode.INSTALLMENT);
        def.setFirstParcel(firstParcel);
        def.setParcelsNumber(parcelsNumber);
        def.setStartDate(startDate);
        def.setSplitMode(SplitMode.EQUAL);
        return def;
    }

    private RecurrenceDefinition recurringDef(String description, BigDecimal amt, LocalDate startDate, LocalDate endDate) {
        RecurrenceDefinition def = new RecurrenceDefinition();
        def.setPlan(plan);
        def.setCreatedBy(user);
        def.setCategory(null);
        def.setSeriesId(SERIES_ID);
        def.setType(FinancialTransactionType.DEBIT);
        def.setAmount(amt);
        def.setDescription(description);
        def.setMode(RecurrenceMode.RECURRING);
        def.setStartDate(startDate);
        def.setEndDate(endDate);
        def.setSplitMode(SplitMode.EQUAL);
        return def;
    }

    /** Builds existing installment occurrences 1..total for the given base description/amount/start, as the
     * create-time generator would have produced them. */
    private List<FinancialTransaction> existingInstallments(String description, BigDecimal amt, int total, LocalDate startDate) {
        List<FinancialTransaction> occurrences = new ArrayList<>();
        for (int parcel = 1; parcel <= total; parcel++) {
            FinancialTransaction tx = new FinancialTransaction();
            tx.setPlan(plan);
            tx.setCreatedBy(user);
            tx.setSeriesId(SERIES_ID);
            tx.setType(FinancialTransactionType.DEBIT);
            tx.setAmount(amt);
            tx.setDescription(description + " (" + parcel + "/" + total + ")");
            tx.setStartDate(startDate.plusMonths(parcel - 1));
            tx.setSplitMode(SplitMode.EQUAL);
            tx.setParcelsNumber(total);
            occurrences.add(tx);
        }
        return occurrences;
    }

    private List<FinancialTransaction> existingRecurring(String description, BigDecimal amt, LocalDate startDate, LocalDate endDate) {
        List<FinancialTransaction> occurrences = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusMonths(1)) {
            FinancialTransaction tx = new FinancialTransaction();
            tx.setPlan(plan);
            tx.setCreatedBy(user);
            tx.setSeriesId(SERIES_ID);
            tx.setType(FinancialTransactionType.DEBIT);
            tx.setAmount(amt);
            tx.setDescription(description);
            tx.setStartDate(date);
            tx.setSplitMode(SplitMode.EQUAL);
            occurrences.add(tx);
        }
        return occurrences;
    }

    @Test
    void thisAndFollowingMidSeriesLeavesEarlierInstallmentsUntouched() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        RecurrenceDefinition def = installmentDef("New Rent", amount, 1, 6, start);
        List<FinancialTransaction> existing = existingInstallments("Rent", amount, 6, start);
        LocalDate pivotDate = existing.get(2).getStartDate();

        SeriesRegenerator.SeriesEditResult result = regenerator.reconcile(
                def, existing, shares, SeriesEditScope.THIS_AND_FOLLOWING, pivotDate);

        assertThat(result.toCreate()).isEmpty();
        assertThat(result.toDelete()).isEmpty();
        assertThat(result.toUpdate()).hasSize(4);
        assertThat(result.toUpdate()).extracting(FinancialTransaction::getDescription)
                .containsExactly("New Rent (3/6)", "New Rent (4/6)", "New Rent (5/6)", "New Rent (6/6)");
        assertThat(result.toUpdate()).doesNotContain(existing.get(0), existing.get(1));
    }

    @Test
    void allScopeTouchesEveryOccurrenceWhenCountUnchanged() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        RecurrenceDefinition def = installmentDef("New Rent", amount, 1, 6, start);
        List<FinancialTransaction> existing = existingInstallments("Rent", amount, 6, start);

        SeriesRegenerator.SeriesEditResult result = regenerator.reconcile(
                def, existing, shares, SeriesEditScope.ALL, null);

        assertThat(result.toUpdate()).hasSize(6);
        assertThat(result.toCreate()).isEmpty();
        assertThat(result.toDelete()).isEmpty();
        assertThat(result.toUpdate()).containsExactlyInAnyOrderElementsOf(existing);
    }

    @Test
    void thisAndFollowingFromFirstOccurrenceMatchesAllScope() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        RecurrenceDefinition def = installmentDef("New Rent", amount, 1, 6, start);
        List<FinancialTransaction> existingForAll = existingInstallments("Rent", amount, 6, start);
        List<FinancialTransaction> existingForFollowing = existingInstallments("Rent", amount, 6, start);

        SeriesRegenerator.SeriesEditResult allResult = regenerator.reconcile(
                def, existingForAll, shares, SeriesEditScope.ALL, null);
        SeriesRegenerator.SeriesEditResult followingResult = regenerator.reconcile(
                def, existingForFollowing, shares, SeriesEditScope.THIS_AND_FOLLOWING, start);

        assertThat(followingResult.toUpdate()).hasSize(allResult.toUpdate().size());
        assertThat(followingResult.toCreate()).isEmpty();
        assertThat(followingResult.toDelete()).isEmpty();
        assertThat(followingResult.toUpdate()).extracting(FinancialTransaction::getDescription)
                .containsExactlyInAnyOrderElementsOf(
                        allResult.toUpdate().stream().map(FinancialTransaction::getDescription).toList());
    }

    @Test
    void installmentCountIncreaseUnderAllCreatesTrailingOccurrences() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        RecurrenceDefinition def = installmentDef("Rent", amount, 1, 9, start);
        List<FinancialTransaction> existing = existingInstallments("Rent", amount, 6, start);

        SeriesRegenerator.SeriesEditResult result = regenerator.reconcile(
                def, existing, shares, SeriesEditScope.ALL, null);

        assertThat(result.toDelete()).isEmpty();
        assertThat(result.toUpdate()).hasSize(6);
        assertThat(result.toUpdate()).extracting(FinancialTransaction::getDescription)
                .containsExactly("Rent (1/9)", "Rent (2/9)", "Rent (3/9)", "Rent (4/9)", "Rent (5/9)", "Rent (6/9)");

        assertThat(result.toCreate()).hasSize(3);
        assertThat(result.toCreate()).extracting(FinancialTransaction::getDescription)
                .containsExactly("Rent (7/9)", "Rent (8/9)", "Rent (9/9)");
        assertThat(result.toCreate()).extracting(FinancialTransaction::getStartDate)
                .containsExactly(start.plusMonths(6), start.plusMonths(7), start.plusMonths(8));
        assertThat(result.toCreate()).allSatisfy(tx -> {
            assertThat(tx.getPlan()).isSameAs(plan);
            assertThat(tx.getCreatedBy()).isSameAs(user);
            assertThat(tx.getSeriesId()).isEqualTo(SERIES_ID);
            assertThat(tx.getRecurrenceDefinition()).isSameAs(def);
            assertThat(tx.getParcelsNumber()).isEqualTo(9);
            assertThat(tx.getParticipants()).hasSize(1);
        });
    }

    @Test
    void installmentCountDecreaseUnderAllDeletesTrailingOccurrences() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        RecurrenceDefinition def = installmentDef("Rent", amount, 1, 4, start);
        List<FinancialTransaction> existing = existingInstallments("Rent", amount, 6, start);

        SeriesRegenerator.SeriesEditResult result = regenerator.reconcile(
                def, existing, shares, SeriesEditScope.ALL, null);

        assertThat(result.toCreate()).isEmpty();
        assertThat(result.toUpdate()).hasSize(4);
        assertThat(result.toUpdate()).extracting(FinancialTransaction::getDescription)
                .containsExactly("Rent (1/4)", "Rent (2/4)", "Rent (3/4)", "Rent (4/4)");

        assertThat(result.toDelete()).hasSize(2);
        assertThat(result.toDelete()).containsExactly(existing.get(4), existing.get(5));
    }

    @Test
    void recurringEndDateExtendUnderAllCreatesNewMonths() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate oldEnd = LocalDate.of(2026, 6, 1);
        LocalDate newEnd = LocalDate.of(2026, 8, 1);
        RecurrenceDefinition def = recurringDef("Gym", amount, start, newEnd);
        List<FinancialTransaction> existing = existingRecurring("Gym", amount, start, oldEnd);

        SeriesRegenerator.SeriesEditResult result = regenerator.reconcile(
                def, existing, shares, SeriesEditScope.ALL, null);

        assertThat(result.toDelete()).isEmpty();
        assertThat(result.toUpdate()).hasSize(6);
        assertThat(result.toCreate()).hasSize(2);
        assertThat(result.toCreate()).extracting(FinancialTransaction::getStartDate)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
        assertThat(result.toCreate()).allSatisfy(tx -> {
            assertThat(tx.getDescription()).isEqualTo("Gym");
            assertThat(tx.getParcelsNumber()).isNull();
            assertThat(tx.getRecurrenceDefinition()).isSameAs(def);
        });
    }

    @Test
    void recurringEndDateShortenUnderAllDeletesTrailingMonths() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate oldEnd = LocalDate.of(2026, 6, 1);
        LocalDate newEnd = LocalDate.of(2026, 3, 1);
        RecurrenceDefinition def = recurringDef("Gym", amount, start, newEnd);
        List<FinancialTransaction> existing = existingRecurring("Gym", amount, start, oldEnd);

        SeriesRegenerator.SeriesEditResult result = regenerator.reconcile(
                def, existing, shares, SeriesEditScope.ALL, null);

        assertThat(result.toCreate()).isEmpty();
        assertThat(result.toUpdate()).hasSize(3);
        assertThat(result.toUpdate()).extracting(FinancialTransaction::getStartDate)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));

        assertThat(result.toDelete()).hasSize(3);
        assertThat(result.toDelete()).extracting(FinancialTransaction::getStartDate)
                .containsExactly(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));
    }

    @Test
    void recurringNullEndDateUnderAllExtendsToRollingHorizon() {
        // P3 re-open: a null endDate on a RECURRING definition means open-ended, so the target list
        // must fall back to the rolling 12-month horizon (from the injected FIXED_CLOCK's "today")
        // instead of NPE-ing on def.getEndDate(). start's day-of-month (1) is preserved by the
        // monthly stepping, so the last generated slot is the last day-1 date not after the horizon.
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate oldEnd = LocalDate.of(2026, 3, 1);
        // FIXED_CLOCK's "today" is 2026-07-24 -> horizon cap 2027-07-24; the last day-1 slot not
        // after that cap is 2027-07-01 (2027-08-01 would be after it).
        LocalDate expectedLastGenerated = LocalDate.of(2027, 7, 1);
        RecurrenceDefinition def = recurringDef("Gym", amount, start, null);
        List<FinancialTransaction> existing = existingRecurring("Gym", amount, start, oldEnd);

        SeriesRegenerator.SeriesEditResult result = regenerator.reconcile(
                def, existing, shares, SeriesEditScope.ALL, null);

        assertThat(result.toDelete()).isEmpty();
        assertThat(result.toUpdate()).hasSize(3);
        assertThat(result.toCreate()).extracting(FinancialTransaction::getStartDate)
                .contains(expectedLastGenerated)
                .doesNotContain(expectedLastGenerated.plusMonths(1));
        assertThat(result.toCreate()).allSatisfy(tx -> {
            assertThat(tx.getDescription()).isEqualTo("Gym");
            assertThat(tx.getRecurrenceDefinition()).isSameAs(def);
        });
    }

    @Test
    void targetCountExceedingCapThrows() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        RecurrenceDefinition def = installmentDef("Rent", amount, 1, 200, start);
        List<FinancialTransaction> existing = existingInstallments("Rent", amount, 6, start);

        assertThatThrownBy(() -> regenerator.reconcile(def, existing, shares, SeriesEditScope.ALL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many occurrences");
    }
}
