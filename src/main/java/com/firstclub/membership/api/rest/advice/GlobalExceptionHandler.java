package com.firstclub.membership.api.rest.advice;

import com.firstclub.membership.common.exception.*;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://firstclub.com/errors/";

    @ExceptionHandler(SubscriptionConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            SubscriptionConflictException ex, WebRequest request) {
        return buildProblem(HttpStatus.CONFLICT, "subscription-conflict",
                "Subscription Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(EligibilityNotMetException.class)
    public ResponseEntity<ProblemDetail> handleEligibilityNotMet(
            EligibilityNotMetException ex, WebRequest request) {
        return buildProblem(HttpStatus.UNPROCESSABLE_ENTITY, "eligibility-not-met",
                "Eligibility Not Met", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleIllegalTransition(
            IllegalStateTransitionException ex, WebRequest request) {
        return buildProblem(HttpStatus.CONFLICT, "invalid-state-transition",
                "Invalid State Transition", ex.getMessage(), request);
    }

    @ExceptionHandler({SubscriptionNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(
            DomainException ex, WebRequest request) {
        return buildProblem(HttpStatus.NOT_FOUND, "resource-not-found",
                "Resource Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ProblemDetail> handlePaymentFailed(
            PaymentFailedException ex, WebRequest request) {
        return buildProblem(HttpStatus.PAYMENT_REQUIRED, "payment-failed",
                "Payment Failed", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyViolationException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyViolation(
            IdempotencyViolationException ex, WebRequest request) {
        return buildProblem(HttpStatus.CONFLICT, "idempotency-violation",
                "Idempotency Violation", ex.getMessage(), request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            Exception ex, WebRequest request) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return buildProblem(HttpStatus.CONFLICT, "concurrent-modification",
                "Concurrent Modification",
                "The resource was modified by another request. Please retry.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildProblem(HttpStatus.BAD_REQUEST, "validation-failed",
                "Validation Failed", detail, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<ProblemDetail> buildProblem(HttpStatus status, String errorType,
                                                        String title, String detail,
                                                        WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(ERROR_BASE_URI + errorType));
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
