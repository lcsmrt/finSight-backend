package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionSeriesRequestDto;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceInterval;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.models.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecurringTransactionGeneratorTest {

    // Fixed "today" so open-ended horizon assertions are deterministic regardless of wall-clock time.
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC);

    private final RecurringTransactionGenerator generator = new RecurringTransactionGenerator(FIXED_CLOCK);

    private final User user = userWithId(1L);
    private final User other = userWithId(2L);
    private final Plan plan = new Plan();
    private final BigDecimal amount = new BigDecimal("300.00");
    private final List<ResolvedParticipant> selfShares = List.of(new ResolvedParticipant(user, amount));
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

    @Test
    void installmentSeriesExpandsToOneOccurrencePerParcel() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Laptop");
        when(dto.getParcelsNumber()).thenReturn(12);
        when(dto.getCurrentParcel()).thenReturn(null);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 15));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(12);

        FinancialTransaction first = result.get(0);
        FinancialTransaction last = result.get(11);
        assertThat(first.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(last.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(first.getDescription()).endsWith("(1/12)");
        assertThat(last.getDescription()).endsWith("(12/12)");

        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getAmount()).isEqualTo(amount);
            assertThat(tx.getParcelsNumber()).isEqualTo(12);
            assertThat(tx.getSeriesId()).isEqualTo(SERIES_ID);
            assertThat(tx.getCreatedBy()).isSameAs(user);
            assertThat(tx.getType()).isEqualTo(FinancialTransactionType.DEBIT);
        });
    }

    @Test
    void inProgressInstallmentGeneratesCurrentParcelThroughLast() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Laptop");
        when(dto.getParcelsNumber()).thenReturn(12);
        when(dto.getCurrentParcel()).thenReturn(5);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 15));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(8);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.get(7).getStartDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(result.get(0).getDescription()).endsWith("(5/12)");
        assertThat(result.get(7).getDescription()).endsWith("(12/12)");
        assertThat(result).allSatisfy(tx -> assertThat(tx.getParcelsNumber()).isEqualTo(12));
    }

    @Test
    void installmentWithCurrentParcelOneMatchesDefaultBehaviour() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Laptop");
        when(dto.getParcelsNumber()).thenReturn(12);
        when(dto.getCurrentParcel()).thenReturn(1);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 15));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(12);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.get(11).getStartDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(result.get(0).getDescription()).endsWith("(1/12)");
        assertThat(result.get(11).getDescription()).endsWith("(12/12)");
    }

    @Test
    void inProgressInstallmentAtLastParcelGeneratesSingleRow() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Laptop");
        when(dto.getParcelsNumber()).thenReturn(12);
        when(dto.getCurrentParcel()).thenReturn(12);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 15));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.get(0).getDescription()).endsWith("(12/12)");
    }

    @Test
    void installmentCapAppliesToGeneratedCountNotTotal() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Laptop");
        when(dto.getParcelsNumber()).thenReturn(200);
        when(dto.getCurrentParcel()).thenReturn(1);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 15));

        assertThatThrownBy(() -> generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many occurrences");

        when(dto.getParcelsNumber()).thenReturn(200);
        when(dto.getCurrentParcel()).thenReturn(81);
        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);
        assertThat(result).hasSize(120);
    }

    @Test
    void recurringMonthlySeriesExpandsInclusiveOfEndDate() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.RECURRING);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Gym membership");
        when(dto.getInterval()).thenReturn(RecurrenceInterval.MONTHLY);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(dto.getEndDate()).thenReturn(LocalDate.of(2026, 12, 1));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(12);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(11).getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));

        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getDescription()).isEqualTo("Gym membership");
            assertThat(tx.getParcelsNumber()).isNull();
            assertThat(tx.getEndDate()).isNull();
            assertThat(tx.getSeriesId()).isEqualTo(SERIES_ID);
        });
    }

    @Test
    void installmentSeriesClampsDayOfMonthOverflow() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Rent deposit");
        when(dto.getParcelsNumber()).thenReturn(2);
        when(dto.getCurrentParcel()).thenReturn(null);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 31));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(result.get(1).getStartDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void recurringSeriesExceedingCapThrows() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.RECURRING);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Forever");
        when(dto.getInterval()).thenReturn(RecurrenceInterval.MONTHLY);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(dto.getEndDate()).thenReturn(LocalDate.of(2050, 1, 1));

        assertThatThrownBy(() -> generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many occurrences");
    }

    @Test
    void installmentSeriesStampsSameParticipantSharesOnEveryOccurrence() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Rent");
        when(dto.getParcelsNumber()).thenReturn(3);
        when(dto.getCurrentParcel()).thenReturn(null);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));

        List<ResolvedParticipant> shares = List.of(
                new ResolvedParticipant(user, new BigDecimal("150.00")),
                new ResolvedParticipant(other, new BigDecimal("150.00")));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, shares);

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getSplitMode()).isEqualTo(SplitMode.EQUAL);
            assertThat(tx.getParticipants()).hasSize(2);
            assertThat(tx.getParticipants()).extracting(p -> p.getMember().getId())
                    .containsExactlyInAnyOrder(user.getId(), other.getId());
            assertThat(tx.getParticipants()).allSatisfy(p -> assertThat(p.getShareAmount()).isEqualByComparingTo("150.00"));
            assertThat(tx.getParticipants()).allSatisfy(p -> assertThat(p.getTransaction()).isSameAs(tx));
        });
    }

    @Test
    void recurringSeriesStampsSameParticipantSharesOnEveryOccurrence() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.RECURRING);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Gym membership");
        when(dto.getInterval()).thenReturn(RecurrenceInterval.MONTHLY);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(dto.getEndDate()).thenReturn(LocalDate.of(2026, 3, 1));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getParticipants()).hasSize(1);
            assertThat(tx.getParticipants().get(0).getMember().getId()).isEqualTo(user.getId());
            assertThat(tx.getParticipants().get(0).getShareAmount()).isEqualByComparingTo(amount);
        });
    }

    @Test
    void recurringSeriesWithEndDateIgnoresClockAndProducesBoundedOutputUnchanged() {
        // Regression: presence of an injected Clock must not alter bounded (endDate-present) output at all.
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.RECURRING);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Gym membership");
        when(dto.getInterval()).thenReturn(RecurrenceInterval.MONTHLY);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(dto.getEndDate()).thenReturn(LocalDate.of(2026, 12, 1));

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        assertThat(result).hasSize(12);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(11).getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void openEndedRecurringSeriesGeneratesTwelveMonthHorizonFromToday() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.RECURRING);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Streaming subscription");
        when(dto.getInterval()).thenReturn(RecurrenceInterval.MONTHLY);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 7, 24));
        when(dto.getEndDate()).thenReturn(null);

        List<FinancialTransaction> result = generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares);

        // "today" is the fixed clock (2026-07-24); horizon = today + 12 months = 2027-07-24, inclusive.
        assertThat(result).hasSize(13);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(result.get(result.size() - 1).getStartDate()).isEqualTo(LocalDate.of(2027, 7, 24));
        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getEndDate()).isNull();
            assertThat(tx.getParcelsNumber()).isNull();
            assertThat(tx.getSeriesId()).isEqualTo(SERIES_ID);
        });
    }

    @Test
    void openEndedRecurringSeriesWithVeryOldStartHitsMaxOccurrencesGuard() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.RECURRING);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Ancient subscription");
        when(dto.getInterval()).thenReturn(RecurrenceInterval.MONTHLY);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2005, 1, 1));
        when(dto.getEndDate()).thenReturn(null);

        // Span from 2005-01-01 to horizon (2027-07-24, fixed clock + 12mo) is ~270 months, well past 120.
        assertThatThrownBy(() -> generator.generate(dto, plan, user, null, SERIES_ID, SplitMode.EQUAL, selfShares))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many occurrences");
    }

    @Test
    void forwardWindowGeneratesOnlyMonthsStrictlyAfterWatermarkThroughHorizon() {
        RecurrenceDefinition def = openEndedDefinition(LocalDate.of(2026, 1, 1));

        List<FinancialTransaction> result = generator.generateForwardWindow(
                def, LocalDate.of(2026, 6, 1), LocalDate.of(2027, 1, 1), selfShares);

        assertThat(result).extracting(FinancialTransaction::getStartDate).containsExactly(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 1, 1));
        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getSeriesId()).isEqualTo(def.getSeriesId());
            assertThat(tx.getRecurrenceDefinition()).isSameAs(def);
            assertThat(tx.getParcelsNumber()).isNull();
            assertThat(tx.getEndDate()).isNull();
            assertThat(tx.getDescription()).isEqualTo(def.getDescription());
        });
    }

    @Test
    void forwardWindowSecondPassAfterAdvancingWatermarkProducesNoOverlap() {
        RecurrenceDefinition def = openEndedDefinition(LocalDate.of(2026, 1, 1));

        List<FinancialTransaction> firstPass = generator.generateForwardWindow(
                def, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1), selfShares);
        assertThat(firstPass).extracting(FinancialTransaction::getStartDate).containsExactly(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        LocalDate advancedWatermark = firstPass.get(firstPass.size() - 1).getStartDate();
        List<FinancialTransaction> secondPass = generator.generateForwardWindow(
                def, advancedWatermark, LocalDate.of(2026, 12, 1), selfShares);

        assertThat(secondPass).extracting(FinancialTransaction::getStartDate).containsExactly(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1));

        List<LocalDate> secondPassDates = secondPass.stream().map(FinancialTransaction::getStartDate).toList();
        assertThat(firstPass).extracting(FinancialTransaction::getStartDate)
                .doesNotContainAnyElementsOf(secondPassDates);
    }

    @Test
    void forwardWindowHonorsMaxOccurrencesCapPerPass() {
        RecurrenceDefinition def = openEndedDefinition(LocalDate.of(2000, 1, 1));

        assertThatThrownBy(() -> generator.generateForwardWindow(
                def, LocalDate.of(2000, 1, 1), LocalDate.of(2020, 1, 1), selfShares))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many occurrences");
    }

    private RecurrenceDefinition openEndedDefinition(LocalDate startDate) {
        RecurrenceDefinition def = new RecurrenceDefinition();
        def.setPlan(plan);
        def.setCreatedBy(user);
        def.setCategory(null);
        def.setSeriesId(SERIES_ID);
        def.setType(FinancialTransactionType.DEBIT);
        def.setAmount(amount);
        def.setDescription("Gym membership");
        def.setMode(RecurrenceMode.RECURRING);
        def.setRecurrenceInterval(RecurrenceInterval.MONTHLY);
        def.setStartDate(startDate);
        def.setEndDate(null);
        def.setSplitMode(SplitMode.EQUAL);
        return def;
    }
}
