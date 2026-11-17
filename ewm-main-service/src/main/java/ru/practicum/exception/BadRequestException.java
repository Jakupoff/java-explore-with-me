package ru.practicum.exception;

/**
 * The request itself is invalid (HTTP 400).
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
