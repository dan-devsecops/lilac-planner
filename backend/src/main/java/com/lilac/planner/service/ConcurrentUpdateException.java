package com.lilac.planner.service;

/**
 * Raised when a concurrent write is detected via optimistic locking (e.g. the JPA
 * {@code @Version} field has been incremented by another transaction since the data
 * was last read). Mapped to HTTP 409 by {@code ApiExceptionHandler}.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message) {
        super(message);
    }

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
