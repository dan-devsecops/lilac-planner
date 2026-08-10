package com.lilac.planner.controller;

import com.lilac.planner.service.AuthExceptions.InvalidCredentialsException;
import com.lilac.planner.service.AuthExceptions.InvalidTokenException;
import com.lilac.planner.service.AuthExceptions.SignupDisabledException;
import com.lilac.planner.service.AuthExceptions.UserAlreadyExistsException;
import com.lilac.planner.service.ConcurrentUpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

/**
 * Central error-to-HTTP mapping. Extends {@link ResponseEntityExceptionHandler}
 * so framework-raised errors (bean validation, malformed JSON, missing params,
 * unknown enum values) also render as RFC 9457 {@code application/problem+json}
 * bodies - one consistent error contract for every failure mode.
 */
@ControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail notFound(NoSuchElementException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    /** Path variables / query params that fail type conversion, e.g. a malformed date. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'");
        problem.setTitle("Invalid parameter");
        return problem;
    }

    /** Concurrent write detected via optimistic locking - client should retry. */
    @ExceptionHandler(ConcurrentUpdateException.class)
    public ProblemDetail concurrentUpdate(ConcurrentUpdateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Concurrent update conflict");
        return problem;
    }

    /**
     * Optimistic-locking failures that escape the adapters' own try/catch. With JPA the
     * version-checked UPDATE runs at commit flush of the {@code @Transactional} service
     * method - after {@code saveDay} has already returned - so the raw Spring exception
     * (parent of both the JPA and Neo4j variants) must map to the same 409, not fall
     * through to the 500 catch-all.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail optimisticLockingConflict(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was modified concurrently - please retry");
        problem.setTitle("Concurrent update conflict");
        return problem;
    }

    /** Registration conflict - username or email already taken. */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail userExists(UserAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("User already exists");
        return problem;
    }

    /** Wrong credentials on login or change-password. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid credentials");
        return problem;
    }

    /** Unknown, expired, or already-used refresh/reset token. */
    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail invalidToken(InvalidTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid token");
        return problem;
    }

    /** Self-service registration is disabled. */
    @ExceptionHandler(SignupDisabledException.class)
    public ProblemDetail signupDisabled(SignupDisabledException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Registration disabled");
        return problem;
    }

    /**
     * Method-security denials (e.g. {@code @PreAuthorize}) on an authenticated-but-unauthorized
     * request surface here; without this they'd fall through to the catch-all as a 500.
     * (Missing-token requests are handled earlier by the resource-server entry point as 401.)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        problem.setTitle("Forbidden");
        return problem;
    }

    /** Last-resort handler: log the full stack trace, expose none of it. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal error");
        return problem;
    }
}
