package com.compliancechecker.controller;

import com.compliancechecker.llm.LlmException;
import com.compliancechecker.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Turns exceptions into {@link ErrorResponse}s.
 *
 * <p>Exception messages are never logged or returned, because parser messages can contain fragments of the prompt.
 * Only the error code, field names and exception type are logged.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleInvalidFields(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new TreeMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			// Messages come from the annotations in CheckRequest and never contain the rejected value.
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid. See fieldErrors.",
				fieldErrors, ex);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
		return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
				"The request body is missing or is not valid JSON.", Map.of(), ex);
	}

	@ExceptionHandler(LlmException.class)
	ResponseEntity<ErrorResponse> handleLlmFailure(LlmException ex) {
		return respond(HttpStatus.BAD_GATEWAY, "LLM_UNAVAILABLE",
				"The compliance check is temporarily unavailable. Please try again.", Map.of(), ex);
	}

	/** Keeps the status of Spring's own errors (404, 405, 415, ...); everything else becomes 500. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleOtherErrors(Exception ex) {
		if (ex instanceof org.springframework.web.ErrorResponse springError) {
			HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
			return respond(status, status.name(), "The request could not be processed: " + status.getReasonPhrase(),
					Map.of(), ex);
		}
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.",
				Map.of(), ex);
	}

	private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String error, String message,
			Map<String, String> fieldErrors, Exception ex) {
		String requestId = UUID.randomUUID().toString();
		if (status.is5xxServerError()) {
			log.error("requestId={} error={} exception={}", requestId, error, ex.getClass().getName());
		}
		else {
			log.warn("requestId={} error={} fields={} exception={}", requestId, error, fieldErrors.keySet(),
					ex.getClass().getSimpleName());
		}
		return ResponseEntity.status(status).body(new ErrorResponse(error, message, fieldErrors, requestId));
	}
}
