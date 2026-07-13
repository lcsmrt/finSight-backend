package com.lcs.finsight.services;

import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.services.SplitResolver.ParticipantInput;
import com.lcs.finsight.services.SplitResolver.ResolvedShare;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitResolverTest {

    private final SplitResolver resolver = new SplitResolver();

    @Test
    void equalSplitDividesEvenlyWhenAmountDividesCleanly() {
        List<ParticipantInput> inputs = List.of(
                new ParticipantInput(1L, null),
                new ParticipantInput(2L, null));

        List<ResolvedShare> shares = resolver.resolve(new BigDecimal("100.00"), SplitMode.EQUAL, inputs);

        assertThat(shares).extracting(ResolvedShare::shareAmount)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("50.00"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @Test
    void equalSplitDistributesResidualCentDeterministicallyToFirstParticipants() {
        List<ParticipantInput> inputs = List.of(
                new ParticipantInput(1L, null),
                new ParticipantInput(2L, null),
                new ParticipantInput(3L, null));

        List<ResolvedShare> shares = resolver.resolve(new BigDecimal("100.00"), SplitMode.EQUAL, inputs);

        assertThat(shares).extracting(ResolvedShare::shareAmount)
                .containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @Test
    void equalSplitWithSingleParticipantIsFullAmount() {
        List<ParticipantInput> inputs = List.of(new ParticipantInput(1L, null));

        List<ResolvedShare> shares = resolver.resolve(new BigDecimal("42.50"), SplitMode.EQUAL, inputs);

        assertThat(shares).hasSize(1);
        assertThat(shares.get(0).shareAmount()).isEqualByComparingTo("42.50");
    }

    @Test
    void exactSplitUsesGivenSharesWhenTheySumToAmount() {
        List<ParticipantInput> inputs = List.of(
                new ParticipantInput(1L, new BigDecimal("70.00")),
                new ParticipantInput(2L, new BigDecimal("30.00")));

        List<ResolvedShare> shares = resolver.resolve(new BigDecimal("100.00"), SplitMode.EXACT, inputs);

        assertThat(shares).extracting(ResolvedShare::shareAmount)
                .containsExactly(new BigDecimal("70.00"), new BigDecimal("30.00"));
    }

    @Test
    void exactSplitRejectsSharesThatDoNotSumToAmount() {
        List<ParticipantInput> inputs = List.of(
                new ParticipantInput(1L, new BigDecimal("70.00")),
                new ParticipantInput(2L, new BigDecimal("20.00")));

        assertThatThrownBy(() -> resolver.resolve(new BigDecimal("100.00"), SplitMode.EXACT, inputs))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void percentModeIsRejected() {
        List<ParticipantInput> inputs = List.of(new ParticipantInput(1L, null));

        assertThatThrownBy(() -> resolver.resolve(new BigDecimal("100.00"), SplitMode.PERCENT, inputs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERCENT not yet supported");
    }

    private static BigDecimal sum(List<ResolvedShare> shares) {
        return shares.stream().map(ResolvedShare::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
