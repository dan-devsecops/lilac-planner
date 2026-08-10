package com.lilac.planner.service;

/**
 * Auth failures raised by {@link NativeAuthService}, mapped to HTTP status codes by
 * {@code ApiExceptionHandler}. Grouped here so the small set stays easy to scan.
 */
public final class AuthExceptions {

    private AuthExceptions() {}

    /** Username or email already registered → 409 Conflict. */
    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String message) { super(message); }
    }

    /** Wrong username/email/password on login → 401 Unauthorized. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() { super("Invalid username or password"); }
    }

    /** Unknown, expired, or already-used refresh/reset token → 401 Unauthorized. */
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) { super(message); }
    }

    /** Self-service registration is disabled → 403 Forbidden. */
    public static class SignupDisabledException extends RuntimeException {
        public SignupDisabledException() { super("Self-service registration is disabled"); }
    }
}
