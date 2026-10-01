package com.smartwatch.user.security;

/**
 * A rejected access token. The message is safe to return to the client.
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String message) {
        super(message);
    }
}
