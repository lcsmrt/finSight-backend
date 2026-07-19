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

    public static class MemberNotFoundException extends RuntimeException {
        public MemberNotFoundException(Long planId, Long userId) {
            super("User " + userId + " is not a member of plan " + planId + ".");
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

    public static class InvitationEmailMismatchException extends RuntimeException {
        public InvitationEmailMismatchException() {
            super("This invitation is bound to a different email address.");
        }
    }

    public static class InvitationRevokedException extends RuntimeException {
        public InvitationRevokedException() {
            super("This invitation has been revoked.");
        }
    }

    public static class InvitationAlreadyUsedException extends RuntimeException {
        public InvitationAlreadyUsedException() {
            super("This invitation has already been used.");
        }
    }

    public static class InvitationExpiredException extends RuntimeException {
        public InvitationExpiredException(String message) {
            super(message);
        }
    }
}
