package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionSeriesRequestDto;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionType;
import com.lcs.finsight.models.RecurrenceInterval;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecurringTransactionGeneratorTest {

    private final RecurringTransactionGenerator generator = new RecurringTransactionGenerator();

    private final User user = new User();
    private final BigDecimal amount = new BigDecimal("300.00");
    private static final String SERIES_ID = "test-series";

    @Test
    void installmentSeriesExpandsToOneOccurrencePerParcel() {
        FinancialTransactionSeriesRequestDto dto = mock(FinancialTransactionSeriesRequestDto.class);
        when(dto.getMode()).thenReturn(RecurrenceMode.INSTALLMENT);
        when(dto.getType()).thenReturn(FinancialTransactionType.DEBIT);
        when(dto.getAmount()).thenReturn(amount);
        when(dto.getDescription()).thenReturn("Laptop");
        when(dto.getParcelsNumber()).thenReturn(12);
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 15));

        List<FinancialTransaction> result = generator.generate(dto, user, null, SERIES_ID);

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
            assertThat(tx.getFrequency()).isNull();
            assertThat(tx.getSeriesId()).isEqualTo(SERIES_ID);
            assertThat(tx.getUser()).isSameAs(user);
            assertThat(tx.getType()).isEqualTo(FinancialTransactionType.DEBIT);
        });
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

        List<FinancialTransaction> result = generator.generate(dto, user, null, SERIES_ID);

        assertThat(result).hasSize(12);
        assertThat(result.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(11).getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));

        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.getFrequency()).isEqualTo("MONTHLY");
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
        when(dto.getStartDate()).thenReturn(LocalDate.of(2026, 1, 31));

        List<FinancialTransaction> result = generator.generate(dto, user, null, SERIES_ID);

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

        assertThatThrownBy(() -> generator.generate(dto, user, null, SERIES_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many occurrences");
    }
}
