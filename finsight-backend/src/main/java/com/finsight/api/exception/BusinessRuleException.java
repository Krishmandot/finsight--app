package com.finsight.api.exception;

import org.springframework.http.HttpStatus;

/**
 * A request that is well-formed but violates a business rule. The message is shown to the user, so it
 * must never contain financial values or personal data.
 */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(String message) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessRuleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static BusinessRuleException conflict(String message) {
        return new BusinessRuleException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
