package com.jansunwai.exception;

import org.springframework.http.HttpStatus;

/** 409: the action is not allowed in the record's current state (invalid workflow transition). */
public class InvalidStateException extends ApiException {
    public InvalidStateException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_STATE", message);
    }
}
