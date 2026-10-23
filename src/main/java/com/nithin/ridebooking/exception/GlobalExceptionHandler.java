package com.nithin.ridebooking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.ErrorResponse;
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final org.slf4j.Logger UNEXPECTED_LOG =
	        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
	    // Framework errors such as 404, 405 and 415 already know their status: keep it
	    if (ex instanceof ErrorResponse errorResponse) {
	        HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
	        return build(status, status.getReasonPhrase());
	    }
	    if (ex instanceof org.springframework.security.core.AuthenticationException) {
	        return build(HttpStatus.UNAUTHORIZED, "Authentication failed");
	    }
	    // Anything else is a bug: log the detail here, show the client nothing
	    UNEXPECTED_LOG.error("Unexpected error", ex);
	    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
	}
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentProvider(PaymentProviderException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, fields);
    }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, Object message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}