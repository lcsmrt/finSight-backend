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

    public static class CategoryTypeMismatchException extends RuntimeException {
        public CategoryTypeMismatchException() {
            super("Category does not match the transaction type.");
        }
    }

    public static class ItemCategoryTypeMismatchException extends RuntimeException {
        public ItemCategoryTypeMismatchException() {
            super("Item category does not match the transaction type.");
        }
    }

    public static class ItemsTotalExceedsAmountException extends RuntimeException {
        public ItemsTotalExceedsAmountException() {
            super("Items total cannot exceed the transaction amount.");
        }
    }

    public static class ParticipantSharesMismatchException extends RuntimeException {
        public ParticipantSharesMismatchException() {
            super("Participant shares must sum to the transaction amount.");
        }
    }
}
