package com.jansunwai.exception;

import org.springframework.http.HttpStatus;

/** 403 for a visible record the caller may not change (e.g. a field officer acting on someone else's assignment). */
public class ForbiddenActionException extends ApiException {
    public ForbiddenActionException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
