package com.jansunwai.ai;

/** A provider call failed. "code" is stored on the failed analysis row (TIMEOUT, RATE_LIMITED, INVALID_OUTPUT, ...). */
public class AiProviderException extends RuntimeException {
    public enum Code { TIMEOUT, RATE_LIMITED, INVALID_OUTPUT, UNAVAILABLE, DIMENSION_MISMATCH }

    private final Code code;

    public AiProviderException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public AiProviderException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }

    public boolean isRetryable() {
        return code == Code.TIMEOUT || code == Code.RATE_LIMITED || code == Code.UNAVAILABLE || code == Code.INVALID_OUTPUT;
    }
}
