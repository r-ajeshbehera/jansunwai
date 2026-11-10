package com.jansunwai.exception;

import org.springframework.http.HttpStatus;

/** Base for expected business errors: each subclass = one HTTP status + a stable code. Unchecked => auto rollback. */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
