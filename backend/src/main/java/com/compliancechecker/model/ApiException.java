package com.compliancechecker.model;

import org.springframework.http.HttpStatus;

/**
 * An expected error with a fixed status, code and message, turned into an {@link ErrorResponse}. The message is fixed
 * server-side wording and must never contain submitted content.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String error;

	public ApiException(HttpStatus status, String error, String message) {
		super(message);
		this.status = status;
		this.error = error;
	}

	public HttpStatus status() {
		return status;
	}

	public String error() {
		return error;
	}
}
