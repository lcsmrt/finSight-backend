package com.lcs.finsight.services;

import com.lcs.finsight.models.SplitMode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a transaction's total amount into per-member shares for a given split mode.
 * Pure logic, no dependencies. The sum of the resolved shares always equals the amount exactly.
 */
@Component
public class SplitResolver {

    public record ParticipantInput(Long memberId, BigDecimal shareAmount) {}

    public record ResolvedShare(Long memberId, BigDecimal shareAmount) {}

    public List<ResolvedShare> resolve(BigDecimal amount, SplitMode mode, List<ParticipantInput> inputs) {
        return switch (mode) {
            case EQUAL -> resolveEqual(amount, inputs);
            case EXACT -> resolveExact(amount, inputs);
            case PERCENT -> throw new IllegalArgumentException("PERCENT not yet supported");
        };
    }

    private List<ResolvedShare> resolveEqual(BigDecimal amount, List<ParticipantInput> inputs) {
        BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);
        long totalCents = scaledAmount.unscaledValue().longValue();
        int n = inputs.size();
        long baseCents = totalCents / n;
        long remainderCents = totalCents % n;

        List<ResolvedShare> shares = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long cents = baseCents + (i < remainderCents ? 1 : 0);
            shares.add(new ResolvedShare(inputs.get(i).memberId(), BigDecimal.valueOf(cents, 2)));
        }
        return shares;
    }

    private List<ResolvedShare> resolveExact(BigDecimal amount, List<ParticipantInput> inputs) {
        BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sum = inputs.stream()
                .map(ParticipantInput::shareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (sum.compareTo(scaledAmount) != 0) {
            throw new IllegalArgumentException("Participant shares must sum to the transaction amount");
        }

        List<ResolvedShare> shares = new ArrayList<>();
        for (ParticipantInput input : inputs) {
            shares.add(new ResolvedShare(input.memberId(), input.shareAmount().setScale(2, RoundingMode.HALF_UP)));
        }
        return shares;
    }
}
