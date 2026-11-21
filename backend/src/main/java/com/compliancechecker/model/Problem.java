package com.compliancechecker.model;

import java.util.regex.Pattern;

/**
 * One finding in a prompt, see {@code problems[]} in API.md.
 *
 * @param ruleId   rule from the compliance file, e.g. {@code R-02}
 * @param severity severity of the rule
 * @param message  explanation for the user
 * @param location the matched passage, verbatim from the prompt, or {@code ""}. Sensitive: only returned to the
 *                 user who sent the prompt, never logged or stored.
 */
public record Problem(String ruleId, Severity severity, String message, String location) {

	public static final String UNSPECIFIED_RULE = "UNSPECIFIED";

	/** Rule ids normally look like {@code R-02}; anything else could be free text from the LLM. */
	private static final Pattern SAFE_RULE_ID = Pattern.compile("[A-Za-z0-9._-]{1,32}");

	/** The rule id if it looks like an id, otherwise {@value #UNSPECIFIED_RULE}. Safe to log and store. */
	public String safeRuleId() {
		return ruleId != null && SAFE_RULE_ID.matcher(ruleId).matches() ? ruleId : UNSPECIFIED_RULE;
	}

	/** Never shows message or location, so an accidental log statement cannot leak them. */
	@Override
	public String toString() {
		return "Problem[ruleId=%s, severity=%s]".formatted(safeRuleId(), severity);
	}
}
