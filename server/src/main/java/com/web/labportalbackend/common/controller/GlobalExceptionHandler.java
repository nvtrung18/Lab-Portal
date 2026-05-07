package com.web.labportalbackend.common.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.common.exception.ApplicationAlreadyReviewedException;
import com.web.labportalbackend.common.exception.ApplicationException;
import com.web.labportalbackend.common.exception.DuplicateApplicationException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API endpoints.
 * Provides consistent error response format across all controllers using Response<T>.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle ResourceNotFoundException (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Response<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Response.notFound(ex.getMessage()));
    }

    /**
     * Handle ApplicationAlreadyReviewedException (409)
     */
    @ExceptionHandler(ApplicationAlreadyReviewedException.class)
    public ResponseEntity<Response<Void>> handleApplicationAlreadyReviewed(ApplicationAlreadyReviewedException ex) {
        log.warn("Application already reviewed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    /**
     * Handle DuplicateApplicationException (409)
     */
    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<Response<Void>> handleDuplicateApplication(DuplicateApplicationException ex) {
        log.warn("Duplicate application attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Response.conflict("Duplicate application", List.of(ex.getMessage())));
    }

    /**
     * Handle ApplicationException (400)
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Response<Void>> handleApplicationException(ApplicationException ex) {
        log.warn("Application error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.badRequest("Application error", List.of(ex.getMessage())));
    }

    /**
     * Handle EntityNotFoundException (404)
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Response<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Response.notFound(ex.getMessage()));
    }

    /**
     * Handle validation errors (400)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.toList());
        
        log.warn("Validation error: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.badRequest("Validation failed", errors));
    }

    /**
     * Handle IllegalArgumentException (400)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.badRequest(ex.getMessage()));
    }

    /**
     * Handle RuntimeException (500)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Response<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception:", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.error("An unexpected error occurred"));
    }

    /**
     * Handle all other exceptions (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected exception:", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.error("An unexpected error occurred"));
    }
}
