package com.compliancechecker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response body of {@code POST /api/check}, exactly as in API.md: {@code isSafe}, {@code problems},
 * {@code improvedVersion}.
 *
 * <p>{@code isSafe} is derived from the problems, so it can never contradict them.
 *
 * @param problems        one entry per match; empty if nothing was found
 * @param improvedVersion sanitised prompt with placeholders like {@code [IBAN_1]}; {@code ""} = no suggestion
 */
public record CheckResponse(List<Problem> problems, String improvedVersion) {

	public CheckResponse {
		problems = List.copyOf(problems);
		improvedVersion = improvedVersion == null ? "" : improvedVersion;
	}

	/** True exactly when no problem has severity critical, high or medium. */
	@JsonProperty("isSafe")
	public boolean isSafe() {
		return problems.stream().noneMatch(problem -> problem.severity().isBlocking());
	}
}
