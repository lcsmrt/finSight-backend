package com.lcs.finsight.exceptions;

public class PlanExceptions {

    public static class PlanNotFoundException extends RuntimeException {
        public PlanNotFoundException(Long id) {
            super("Plan not found for id " + id + ".");
        }
    }

    public static class NotAMemberException extends RuntimeException {
        public NotAMemberException(Long planId) {
            super("Plan not found for id " + planId + ".");
        }
    }

    public static class InsufficientPlanRoleException extends RuntimeException {
        public InsufficientPlanRoleException(String message) {
            super(message);
        }
    }

    public static class CannotModifyOthersTransactionException extends RuntimeException {
        public CannotModifyOthersTransactionException() {
            super("You cannot modify a transaction created by another member.");
        }
    }

    public static class LastPlanException extends RuntimeException {
        public LastPlanException() {
            super("You cannot leave or delete your last remaining plan.");
        }
    }

    public static class LastOwnerException extends RuntimeException {
        public LastOwnerException() {
            super("You cannot remove or demote the last owner of the plan. Transfer ownership first.");
        }
    }

    public static class InvitationNotFoundException extends RuntimeException {
        public InvitationNotFoundException(String token) {
            super("Invitation not found for token " + token + ".");
        }
    }

    public static class InvitationInvalidException extends RuntimeException {
        public InvitationInvalidException(String message) {
            super(message);
        }
    }
}
