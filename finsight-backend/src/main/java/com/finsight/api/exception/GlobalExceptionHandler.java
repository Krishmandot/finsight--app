package com.finsight.api.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into RFC 9457 problem responses for the REST API. Responses never include stack
 * traces, SQL, or submitted values; unexpected errors are logged server-side only.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.finsight.api")
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed", "validation-failed",
            "One or more fields are invalid");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
            .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed", "validation-failed",
            "One or more parameters are invalid");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fieldErrors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ProblemDetail handleMalformedRequest(Exception ex) {
        log.debug("Malformed request: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request", "malformed-request",
            "The request could not be read. Check the parameter names, types and JSON body.");
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class, DisabledException.class})
    public ProblemDetail handleBadCredentials(RuntimeException ex) {
        log.debug("Authentication failed");
        return problem(HttpStatus.UNAUTHORIZED, "Authentication Failed", "authentication-failed",
            "Invalid credentials");
    }

    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(LockedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too Many Attempts", "too-many-attempts",
            "Too many failed sign-in attempts. Try again later.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied");
        return problem(HttpStatus.FORBIDDEN, "Access Denied", "access-denied",
            "You do not have permission to access this resource");
    }

    @ExceptionHandler({EntityNotFoundException.class, ResourceNotFoundException.class, NoResourceFoundException.class})
    public ProblemDetail handleNotFound(Exception ex) {
        log.debug("Resource not found");
        return problem(HttpStatus.NOT_FOUND, "Resource Not Found", "not-found",
            "The requested resource was not found");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(ex.getStatus(), ex.getStatus() == HttpStatus.CONFLICT ? "Conflict" : "Request Not Allowed",
            "business-rule", ex.getMessage());
    }

    @ExceptionHandler(IngestionException.class)
    public ProblemDetail handleIngestion(IngestionException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Import Failed", "import-failed", ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String title = resolved == null ? "Error" : resolved.getReasonPhrase();
        return problem(status, title, "request-failed", ex.getReason() == null ? title : ex.getReason());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File Too Large", "file-too-large",
            "The uploaded file exceeds the maximum allowed size");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "method-not-allowed",
            "This HTTP method is not supported for this resource");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", "unsupported-media-type",
            "This content type is not supported for this resource");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation occurred");
        return problem(HttpStatus.CONFLICT, "Conflict", "conflict",
            "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "internal-error",
            "An unexpected error occurred. Please try again later.");
    }

    private static ProblemDetail problem(HttpStatusCode status, String title, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://finsight.app/errors/" + type));
        return problem;
    }
}
