package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionSeriesRequestDto;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.Plan;
import com.lcs.finsight.models.SplitMode;
import com.lcs.finsight.models.TransactionParticipant;
import com.lcs.finsight.models.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecurringTransactionGenerator {

    private static final int MAX_OCCURRENCES = 120;

    public List<FinancialTransaction> generate(FinancialTransactionSeriesRequestDto dto,
                                               Plan plan,
                                               User createdBy,
                                               FinancialTransactionCategory category,
                                               String seriesId,
                                               SplitMode splitMode,
                                               List<ResolvedParticipant> participantShares) {
        return switch (dto.getMode()) {
            case INSTALLMENT -> generateInstallments(dto, plan, createdBy, category, seriesId, splitMode, participantShares);
            case RECURRING -> generateRecurring(dto, plan, createdBy, category, seriesId, splitMode, participantShares);
        };
    }

    private List<FinancialTransaction> generateInstallments(FinancialTransactionSeriesRequestDto dto,
                                                            Plan plan,
                                                            User createdBy,
                                                            FinancialTransactionCategory category,
                                                            String seriesId,
                                                            SplitMode splitMode,
                                                            List<ResolvedParticipant> participantShares) {
        int total = dto.getParcelsNumber();
        int first = dto.getCurrentParcel() != null ? dto.getCurrentParcel() : 1;
        ensureWithinCap(total - first + 1);

        List<FinancialTransaction> occurrences = new ArrayList<>(total - first + 1);
        for (int parcel = first; parcel <= total; parcel++) {
            FinancialTransaction transaction = baseTransaction(dto, plan, createdBy, category, seriesId,
                    dto.getStartDate().plusMonths(parcel - first), splitMode, participantShares);
            transaction.setDescription(dto.getDescription() + " (" + parcel + "/" + total + ")");
            transaction.setParcelsNumber(total);
            occurrences.add(transaction);
        }
        return occurrences;
    }

    private List<FinancialTransaction> generateRecurring(FinancialTransactionSeriesRequestDto dto,
                                                         Plan plan,
                                                         User createdBy,
                                                         FinancialTransactionCategory category,
                                                         String seriesId,
                                                         SplitMode splitMode,
                                                         List<ResolvedParticipant> participantShares) {
        List<FinancialTransaction> occurrences = new ArrayList<>();
        for (LocalDate date = dto.getStartDate(); !date.isAfter(dto.getEndDate()); date = date.plusMonths(1)) {
            ensureWithinCap(occurrences.size() + 1);

            FinancialTransaction transaction = baseTransaction(dto, plan, createdBy, category, seriesId, date, splitMode, participantShares);
            transaction.setDescription(dto.getDescription());
            transaction.setParcelsNumber(null);
            occurrences.add(transaction);
        }
        return occurrences;
    }

    private FinancialTransaction baseTransaction(FinancialTransactionSeriesRequestDto dto,
                                                 Plan plan,
                                                 User createdBy,
                                                 FinancialTransactionCategory category,
                                                 String seriesId,
                                                 LocalDate date,
                                                 SplitMode splitMode,
                                                 List<ResolvedParticipant> participantShares) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setPlan(plan);
        transaction.setCreatedBy(createdBy);
        transaction.setCategory(category);
        transaction.setType(dto.getType());
        transaction.setAmount(dto.getAmount());
        transaction.setStartDate(date);
        transaction.setEndDate(null);
        transaction.setSeriesId(seriesId);
        transaction.setSplitMode(splitMode);
        for (ResolvedParticipant share : participantShares) {
            TransactionParticipant participant = new TransactionParticipant();
            participant.setTransaction(transaction);
            participant.setMember(share.member());
            participant.setShareAmount(share.shareAmount());
            transaction.getParticipants().add(participant);
        }
        return transaction;
    }

    private void ensureWithinCap(int count) {
        if (count > MAX_OCCURRENCES) {
            throw new IllegalArgumentException(
                    "Series would generate too many occurrences (max " + MAX_OCCURRENCES + ").");
        }
    }
}
