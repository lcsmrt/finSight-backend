package com.lcs.finsight.dtos.response;

public class FinancialTransactionImportResponseDto {
    private final int imported;

    public FinancialTransactionImportResponseDto(int imported) {
        this.imported = imported;
    }

    public int getImported() {
        return imported;
    }
}
