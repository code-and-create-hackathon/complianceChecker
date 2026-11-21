package com.compliancechecker.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/check}, see API.md.
 *
 * <p>The prompt may contain exactly the sensitive data we guard against, so it must never be logged or echoed.
 */
public record CheckRequest(

		@NotBlank(message = "must not be empty")
		@Size(max = 20_000, message = "is too long (max. {max} characters)")
		String prompt,

		@NotBlank(message = "must not be empty")
		@Size(max = 500_000, message = "is too long (max. {max} characters)")
		String compliance) {

	/** Shows only lengths, so an accidental log statement cannot leak content. */
	@Override
	public String toString() {
		return "CheckRequest[prompt=%d chars, compliance=%d chars]".formatted(length(prompt), length(compliance));
	}

	private static int length(String text) {
		return text == null ? 0 : text.length();
	}
}
