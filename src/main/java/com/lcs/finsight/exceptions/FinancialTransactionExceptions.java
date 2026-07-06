package com.lcs.finsight.exceptions;

public class FinancialTransactionExceptions {
    public static class FinancialTransactionNotFoundException extends RuntimeException {
        public FinancialTransactionNotFoundException(Long id) {
            super("Financial transaction not found for id " + id + ".");
        }
    }

    public static class FinancialTransactionSeriesNotFoundException extends RuntimeException {
        public FinancialTransactionSeriesNotFoundException(String seriesId) {
            super("Financial transaction series not found for id " + seriesId + ".");
        }
    }
}
