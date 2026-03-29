package com.lcs.finsight.exceptions;

public class FinancialTransactionCategoryExceptions {
    public static class FinancialTransactionCategoryNotFoundException extends RuntimeException {
        public FinancialTransactionCategoryNotFoundException(Long id) {
            super("Financial transaction category not found for id " + id + ".");
        }
    }
}
