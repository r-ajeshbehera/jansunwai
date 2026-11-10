package com.jansunwai.exception;

import org.springframework.http.HttpStatus;

/** 422: valid request, but it breaks a business rule. */
public class BusinessRuleException extends ApiException {
    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
