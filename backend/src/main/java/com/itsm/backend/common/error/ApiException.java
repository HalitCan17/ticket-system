package com.itsm.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected business errors. Rendered as RFC 9457 ProblemDetail by
 * {@link GlobalExceptionHandler} with a machine-readable {@code code}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
