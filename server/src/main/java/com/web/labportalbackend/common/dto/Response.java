package com.web.labportalbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Unified API response wrapper with code-based status.
 * <p>
 * Provides a standardized response format for all REST endpoints.
 * Uses a numeric code for machine-readable status and message for human-readable context.
 *
 * @param <T> the type of data payload
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> {

    /**
     * Numeric response code:
     * - 0: Success
     * - 400: Bad Request (validation error)
     * - 401: Unauthorized
     * - 403: Forbidden
     * - 404: Not Found
     * - 409: Conflict (duplicate, etc.)
     * - 500: Internal Server Error
     */
    private final int code;

    /**
     * Human-readable message describing the result
     */
    private final String message;

    /**
     * Response data payload (null for errors)
     */
    private final T data;

    /**
     * List of error messages (for validation errors, etc.)
     */
    private final List<String> errors;

    /**
     * Timestamp when the response was generated
     */
    @Builder.Default
    private final Instant timestamp = Instant.now();

    // ---- Factory methods ----

    /**
     * Success response with data
     */
    public static <T> Response<T> ok(String message, T data) {
        return Response.<T>builder()
                .code(0)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Success response with data (default message)
     */
    public static <T> Response<T> ok(T data) {
        return ok("Operation completed successfully", data);
    }

    /**
     * Success response without data
     */
    public static <T> Response<T> ok(String message) {
        return Response.<T>builder()
                .code(0)
                .message(message)
                .build();
    }

    /**
     * Bad request error (validation failure)
     */
    public static <T> Response<T> badRequest(String message, List<String> errors) {
        return Response.<T>builder()
                .code(400)
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * Bad request error (single error)
     */
    public static <T> Response<T> badRequest(String message) {
        return Response.<T>builder()
                .code(400)
                .message(message)
                .build();
    }

    /**
     * Unauthorized error
     */
    public static <T> Response<T> unauthorized(String message) {
        return Response.<T>builder()
                .code(401)
                .message(message)
                .build();
    }

    /**
     * Forbidden error
     */
    public static <T> Response<T> forbidden(String message) {
        return Response.<T>builder()
                .code(403)
                .message(message)
                .build();
    }

    /**
     * Not found error
     */
    public static <T> Response<T> notFound(String message) {
        return Response.<T>builder()
                .code(404)
                .message(message)
                .build();
    }

    /**
     * Conflict error (duplicate, constraint violation, etc.)
     */
    public static <T> Response<T> conflict(String message) {
        return Response.<T>builder()
                .code(409)
                .message(message)
                .build();
    }

    /**
     * Conflict error with details
     */
    public static <T> Response<T> conflict(String message, List<String> errors) {
        return Response.<T>builder()
                .code(409)
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * Internal server error
     */
    public static <T> Response<T> error(String message) {
        return Response.<T>builder()
                .code(500)
                .message(message)
                .build();
    }

    /**
     * Internal server error with details
     */
    public static <T> Response<T> error(String message, List<String> errors) {
        return Response.<T>builder()
                .code(500)
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * Generic error with custom code
     */
    public static <T> Response<T> error(int code, String message) {
        return Response.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    /**
     * Check if response represents success (code 0)
     */
    public boolean isSuccess() {
        return this.code == 0;
    }

    /**
     * Check if response represents an error (code != 0)
     */
    public boolean isError() {
        return this.code != 0;
    }
}
