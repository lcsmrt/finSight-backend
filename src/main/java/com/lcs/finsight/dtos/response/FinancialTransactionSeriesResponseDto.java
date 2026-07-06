package com.lcs.finsight.dtos.response;

import com.lcs.finsight.models.FinancialTransaction;

import java.util.List;

public class FinancialTransactionSeriesResponseDto {
    private final String seriesId;
    private final int count;
    private final List<FinancialTransactionResponseDto> occurrences;

    public FinancialTransactionSeriesResponseDto(List<FinancialTransaction> transactions) {
        this.occurrences = transactions.stream()
                .map(FinancialTransactionResponseDto::new)
                .toList();
        this.count = transactions.isEmpty() ? 0 : transactions.size();
        this.seriesId = transactions.isEmpty() ? null : transactions.get(0).getSeriesId();
    }

    public String getSeriesId() {
        return seriesId;
    }

    public int getCount() {
        return count;
    }

    public List<FinancialTransactionResponseDto> getOccurrences() {
        return occurrences;
    }
}
