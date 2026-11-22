package com.compliancechecker.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/check}, see API.md. The compliance rules are not part of the request: the backend
 * always uses the policy the administrator published.
 *
 * <p>The prompt may contain exactly the sensitive data we guard against, so it must never be logged or echoed.
 */
public record CheckRequest(

		@NotBlank(message = "must not be empty")
		@Size(max = 20_000, message = "is too long (max. {max} characters)")
		String prompt) {

	/** Shows only the length, so an accidental log statement cannot leak content. */
	@Override
	public String toString() {
		return "CheckRequest[prompt=%d chars]".formatted(prompt == null ? 0 : prompt.length());
	}
}
