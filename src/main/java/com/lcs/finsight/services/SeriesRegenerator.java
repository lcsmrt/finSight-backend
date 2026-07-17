package com.lcs.finsight.services;

import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.RecurrenceDefinition;
import com.lcs.finsight.models.RecurrenceMode;
import com.lcs.finsight.models.SeriesEditScope;
import com.lcs.finsight.models.TransactionParticipant;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, dependency-free reconciliation of a series' occurrences against its (already updated)
 * {@link RecurrenceDefinition} template. Given the definition's post-edit field values, the
 * series' existing occurrence rows, resolved participant shares, an edit scope and a pivot date,
 * computes which occurrences must be updated in place, created, or deleted — with position-derived
 * installment {@code k/N} relabeling.
 *
 * <p>Contains zero Spring/JPA dependencies so it can be exercised with plain JUnit, no DB, no context.
 */
@Component
public class SeriesRegenerator {

    private static final int MAX_OCCURRENCES = 120;

    public record SeriesEditResult(
            List<FinancialTransaction> toUpdate,
            List<FinancialTransaction> toCreate,
            List<FinancialTransaction> toDelete
    ) {
    }

    /** A single target slot computed from the (post-edit) recurrence definition template. */
    private record TargetOccurrence(LocalDate date, String description, Integer parcelsNumber, String frequency) {
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
            targets.add(new TargetOccurrence(date, description, total, null));
        }
        return targets;
    }

    private List<TargetOccurrence> buildRecurringTargets(RecurrenceDefinition def) {
        List<TargetOccurrence> targets = new ArrayList<>();
        for (LocalDate date = def.getStartDate(); !date.isAfter(def.getEndDate()); date = date.plusMonths(1)) {
            targets.add(new TargetOccurrence(date, def.getDescription(), null, "MONTHLY"));
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
        tx.setFrequency(slot.frequency());

        tx.getParticipants().clear();
        for (ResolvedParticipant share : shares) {
            TransactionParticipant participant = new TransactionParticipant();
            participant.setTransaction(tx);
            participant.setMember(share.member());
            participant.setShareAmount(share.shareAmount());
            tx.getParticipants().add(participant);
        }
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
