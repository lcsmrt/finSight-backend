package com.lcs.finsight.exceptions;

public class FinancialTransactionExceptions {
    public static class FinancialTransactionNotFoundException extends RuntimeException {
        public FinancialTransactionNotFoundException(Long id) {
            super("Financial transaction not found for id " + id + ".");
        }
    }
}
