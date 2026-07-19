package com.lcs.finsight.exceptions;

public class UserExceptions {

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String email) {
            super("Email " + email + " is already in use.");
        }
    }

    public static class UsernameNotFoundException extends RuntimeException {
        public UsernameNotFoundException(String email) {
            super("User not found for email " + email + ".");
        }
    }
}
