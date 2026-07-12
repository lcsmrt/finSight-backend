package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.FinancialTransactionSeriesRequestDto;
import com.lcs.finsight.models.FinancialTransaction;
import com.lcs.finsight.models.FinancialTransactionCategory;
import com.lcs.finsight.models.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecurringTransactionGenerator {

    private static final int MAX_OCCURRENCES = 120;

    public List<FinancialTransaction> generate(FinancialTransactionSeriesRequestDto dto,
                                               User user,
                                               FinancialTransactionCategory category,
                                               String seriesId) {
        return switch (dto.getMode()) {
            case INSTALLMENT -> generateInstallments(dto, user, category, seriesId);
            case RECURRING -> generateRecurring(dto, user, category, seriesId);
        };
    }

    private List<FinancialTransaction> generateInstallments(FinancialTransactionSeriesRequestDto dto,
                                                            User user,
                                                            FinancialTransactionCategory category,
                                                            String seriesId) {
        int total = dto.getParcelsNumber();
        int first = dto.getCurrentParcel() != null ? dto.getCurrentParcel() : 1;
        ensureWithinCap(total - first + 1);

        List<FinancialTransaction> occurrences = new ArrayList<>(total - first + 1);
        for (int parcel = first; parcel <= total; parcel++) {
            FinancialTransaction transaction = baseTransaction(dto, user, category, seriesId,
                    dto.getStartDate().plusMonths(parcel - first));
            transaction.setDescription(dto.getDescription() + " (" + parcel + "/" + total + ")");
            transaction.setParcelsNumber(total);
            transaction.setFrequency(null);
            occurrences.add(transaction);
        }
        return occurrences;
    }

    private List<FinancialTransaction> generateRecurring(FinancialTransactionSeriesRequestDto dto,
                                                         User user,
                                                         FinancialTransactionCategory category,
                                                         String seriesId) {
        List<FinancialTransaction> occurrences = new ArrayList<>();
        for (LocalDate date = dto.getStartDate(); !date.isAfter(dto.getEndDate()); date = date.plusMonths(1)) {
            ensureWithinCap(occurrences.size() + 1);

            FinancialTransaction transaction = baseTransaction(dto, user, category, seriesId, date);
            transaction.setDescription(dto.getDescription());
            transaction.setParcelsNumber(null);
            transaction.setFrequency("MONTHLY");
            occurrences.add(transaction);
        }
        return occurrences;
    }

    private FinancialTransaction baseTransaction(FinancialTransactionSeriesRequestDto dto,
                                                 User user,
                                                 FinancialTransactionCategory category,
                                                 String seriesId,
                                                 LocalDate date) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setType(dto.getType());
        transaction.setAmount(dto.getAmount());
        transaction.setStartDate(date);
        transaction.setEndDate(null);
        transaction.setSeriesId(seriesId);
        return transaction;
    }

    private void ensureWithinCap(int count) {
        if (count > MAX_OCCURRENCES) {
            throw new IllegalArgumentException(
                    "Series would generate too many occurrences (max " + MAX_OCCURRENCES + ").");
        }
    }
}
