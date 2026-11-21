package com.compliancechecker.controller;

import com.compliancechecker.llm.LlmException;
import com.compliancechecker.llm.InvalidNovaResponseException;
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
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Turns exceptions into {@link ErrorResponse}s.
 *
 * <p>LLM errors include their underlying message in the response for diagnosis. Logs contain only the error code,
 * field names and exception type.
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
		String code = "LLM_INVALID_RESPONSE";
		String message = "Nova hat kein gültiges Prüfergebnis geliefert. Bitte erneut versuchen.";
		if ("Simulated compliance result rejected by the live API.".equals(ex.getMessage())) {
			code = "LLM_SIMULATED_RESULT";
			message = "Ein Demo-Ergebnis wurde verworfen. Bitte starten Sie die Nova-Live-Version.";
		} else if (ex.getCause() instanceof AwsServiceException aws) {
			if (aws.statusCode() == 401 || aws.statusCode() == 403) {
				code = "LLM_ACCESS_DENIED";
				message = "AWS verweigert den Nova-Zugriff. Prüfe die Bedrock-Berechtigung und Zugangsdaten.";
			} else {
				code = "LLM_AWS_ERROR";
				message = "Amazon Bedrock hat die Anfrage abgelehnt (HTTP " + aws.statusCode() + ").";
			}
		} else if (ex.getCause() instanceof software.amazon.awssdk.core.exception.SdkException) {
			code = "LLM_CONNECTION_ERROR";
			message = "Amazon Bedrock ist nicht erreichbar oder die Zugangsdaten sind ungültig.";
		} else if ("Nova credentials have not been entered".equals(ex.getMessage())) {
			code = "LLM_NOT_CONNECTED";
			message = "Bitte zuerst die AWS-Zugangsdaten in der Nova-Konsole eingeben.";
		}
		if (!"LLM_SIMULATED_RESULT".equals(code)) {
			Throwable cause = ex.getCause();
			String details = cause != null && cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
			if (details != null && !details.isBlank()) {
				message += " Details: " + details;
			}
		}
		return respond(HttpStatus.BAD_GATEWAY, code, message, Map.of(), ex);
	}

	@ExceptionHandler(InvalidNovaResponseException.class)
	ResponseEntity<Map<String, Object>> handleInvalidNovaResponse(InvalidNovaResponseException ex) {
		String requestId = UUID.randomUUID().toString();
		log.warn("requestId={} status=LLM_UNVERIFIED_RESPONSE exception={}", requestId, ex.getClass().getName());
		return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
				.body(Map.of("verification", "incomplete", "problems", java.util.List.of(),
						"improvedVersion", ex.responseText(), "requestId", requestId));
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
