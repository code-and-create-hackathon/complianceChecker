package com.compliancechecker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response body of {@code POST /api/check}, see API.md.
 *
 * @param isSafe          whether the prompt may be sent to the target AI system as is
 * @param problems        human-readable findings
 * @param categories      coarse categories of the findings
 * @param improvedVersion sanitized prompt with sensitive values replaced by placeholders
 * @param simulated       true if the result comes from the rule-based mock, not from a real compliance check
 */
public record CheckResponse(
		@JsonProperty("isSafe") boolean isSafe,
		List<String> problems,
		List<ProblemCategory> categories,
		String improvedVersion,
		boolean simulated,
		List<Finding> findings) {

	public CheckResponse(boolean isSafe, List<String> problems, List<ProblemCategory> categories,
			String improvedVersion, boolean simulated) {
		this(isSafe, problems, categories, improvedVersion, simulated, List.of());
	}
}
