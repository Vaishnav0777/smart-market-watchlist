package com.smartwatch.common.web;

import org.springframework.http.HttpStatus;

/**
 * Expected API failure with a client-safe message.
 * The message must not contain passwords, tokens, or secrets.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
