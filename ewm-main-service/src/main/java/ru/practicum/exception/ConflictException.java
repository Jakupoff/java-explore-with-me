package ru.practicum.exception;

/**
 * The request is valid, but breaks a business rule (HTTP 409).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
