package com.lcs.finsight.services;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SeriesEditScope;
import com.lcs.finsight.models.TransactionParticipant;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, dependency-free (besides a {@link Clock}) reconciliation of a series' occurrences against
 * its (already updated) {@link RecurrenceDefinition} template. Given the definition's post-edit
 * field values, the series' existing occurrence rows, resolved participant shares, an edit scope
 * and a pivot date, computes which occurrences must be updated in place, created, or deleted —
 * with position-derived installment {@code k/N} relabeling.
 *
 * <p>Contains zero Spring/JPA dependencies (only a {@link Clock}, plain JDK) so it can be exercised
 * with plain JUnit, no DB, no context.
 */
@Component
public class SeriesRegenerator {

    private static final int MAX_OCCURRENCES = 120;

    /**
     * Rolling look-ahead horizon (in months) applied when a RECURRING definition's {@code endDate}
     * is {@code null} (open-ended series, P3 re-opened via {@code editSeries}). Mirrors {@code
     * RecurringTransactionGenerator.OPEN_ENDED_HORIZON_MONTHS} / {@code
     * OpenEndedSeriesTopUpService.HORIZON_MONTHS}.
     */
    private static final int OPEN_ENDED_HORIZON_MONTHS = 12;

    private final Clock clock;

    public SeriesRegenerator(Clock clock) {
        this.clock = clock;
    }

    public record SeriesEditResult(
            List<FinancialTransaction> toUpdate,
            List<FinancialTransaction> toCreate,
            List<FinancialTransaction> toDelete
    ) {
    }

    /** A single target slot computed from the (post-edit) recurrence definition template. */
    private record TargetOccurrence(LocalDate date, String description, Integer parcelsNumber) {
    }

    public SeriesEditResult reconcile(
            RecurrenceDefinition def,
            List<FinancialTransaction> existing,
            List<ResolvedParticipant> shares,
            SeriesEditScope scope,
            LocalDate pivotDate
    ) {
        List<TargetOccurrence> fullTargets = buildFullTargetList(def);
        ensureWithinCap(fullTargets.size());

        boolean filterByPivot = scope != SeriesEditScope.ALL;

        List<TargetOccurrence> inScopeTargets = filterByPivot
                ? fullTargets.stream().filter(t -> !t.date().isBefore(pivotDate)).toList()
                : fullTargets;

        List<FinancialTransaction> inScopeExisting = existing.stream()
                .filter(tx -> !filterByPivot || !tx.getStartDate().isBefore(pivotDate))
                .sorted(Comparator.comparing(FinancialTransaction::getStartDate))
                .toList();

        List<FinancialTransaction> toUpdate = new ArrayList<>();
        List<FinancialTransaction> toCreate = new ArrayList<>();
        List<FinancialTransaction> toDelete = new ArrayList<>();

        int matched = Math.min(inScopeExisting.size(), inScopeTargets.size());

        for (int i = 0; i < matched; i++) {
            FinancialTransaction tx = inScopeExisting.get(i);
            stampOccurrence(tx, inScopeTargets.get(i), def, shares);
            toUpdate.add(tx);
        }

        for (int i = matched; i < inScopeTargets.size(); i++) {
            toCreate.add(buildNewOccurrence(inScopeTargets.get(i), def, shares));
        }

        for (int i = matched; i < inScopeExisting.size(); i++) {
            toDelete.add(inScopeExisting.get(i));
        }

        return new SeriesEditResult(toUpdate, toCreate, toDelete);
    }

    private List<TargetOccurrence> buildFullTargetList(RecurrenceDefinition def) {
        return def.getMode() == RecurrenceMode.INSTALLMENT
                ? buildInstallmentTargets(def)
                : buildRecurringTargets(def);
    }

    private List<TargetOccurrence> buildInstallmentTargets(RecurrenceDefinition def) {
        int total = def.getParcelsNumber();
        int first = def.getFirstParcel();
        List<TargetOccurrence> targets = new ArrayList<>(Math.max(0, total - first + 1));
        for (int parcel = first; parcel <= total; parcel++) {
            LocalDate date = def.getStartDate().plusMonths(parcel - first);
            String description = def.getDescription() + " (" + parcel + "/" + total + ")";
            targets.add(new TargetOccurrence(date, description, total));
        }
        return targets;
    }

    private List<TargetOccurrence> buildRecurringTargets(RecurrenceDefinition def) {
        // A null endDate means the (re-)opened series is open-ended (P3, full-replace semantics):
        // regenerate out to the rolling horizon, exactly as create-time generation would.
        LocalDate effectiveEnd = def.getEndDate() != null
                ? def.getEndDate()
                : LocalDate.now(clock).plusMonths(OPEN_ENDED_HORIZON_MONTHS);

        List<TargetOccurrence> targets = new ArrayList<>();
        for (LocalDate date = def.getStartDate(); !date.isAfter(effectiveEnd); date = date.plusMonths(1)) {
            targets.add(new TargetOccurrence(date, def.getDescription(), null));
        }
        return targets;
    }

    private void stampOccurrence(FinancialTransaction tx, TargetOccurrence slot, RecurrenceDefinition def,
                                  List<ResolvedParticipant> shares) {
        tx.setAmount(def.getAmount());
        tx.setDescription(slot.description());
        tx.setCategory(def.getCategory());
        tx.setStartDate(slot.date());
        tx.setSplitMode(def.getSplitMode());
        tx.setParcelsNumber(slot.parcelsNumber());

        reconcileParticipants(tx, shares);
    }

    private void reconcileParticipants(FinancialTransaction tx, List<ResolvedParticipant> shares) {
        Map<Long, TransactionParticipant> existingByMemberId = new HashMap<>();
        for (TransactionParticipant participant : tx.getParticipants()) {
            existingByMemberId.put(participant.getMember().getId(), participant);
        }

        Set<Long> keptMemberIds = new HashSet<>();
        for (ResolvedParticipant share : shares) {
            keptMemberIds.add(share.member().getId());
            TransactionParticipant existing = existingByMemberId.get(share.member().getId());
            if (existing != null) {
                existing.setShareAmount(share.shareAmount());
            } else {
                TransactionParticipant participant = new TransactionParticipant();
                participant.setTransaction(tx);
                participant.setMember(share.member());
                participant.setShareAmount(share.shareAmount());
                tx.getParticipants().add(participant);
            }
        }

        tx.getParticipants().removeIf(
                participant -> !keptMemberIds.contains(participant.getMember().getId()));
    }

    private FinancialTransaction buildNewOccurrence(TargetOccurrence slot, RecurrenceDefinition def,
                                                      List<ResolvedParticipant> shares) {
        FinancialTransaction tx = new FinancialTransaction();
        tx.setPlan(def.getPlan());
        tx.setCreatedBy(def.getCreatedBy());
        tx.setSeriesId(def.getSeriesId());
        tx.setRecurrenceDefinition(def);
        tx.setType(def.getType());
        tx.setEndDate(null);
        stampOccurrence(tx, slot, def, shares);
        return tx;
    }

    private void ensureWithinCap(int count) {
        if (count > MAX_OCCURRENCES) {
            throw new IllegalArgumentException(
                    "Series would generate too many occurrences (max " + MAX_OCCURRENCES + ").");
        }
    }
}
