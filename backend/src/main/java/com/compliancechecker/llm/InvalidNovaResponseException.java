package com.compliancechecker.llm;

/**
 * Nova reported no problems but did not return the complete input, also on the second attempt: the check is
 * incomplete. The API answers with {@code verification: "incomplete"} and the unchecked model output (API.md); the
 * prompt is not approved.
 */
public class InvalidNovaResponseException extends LlmException {

	static final String MSG_INCOMPLETE = "Nova returned an incomplete or contradictory answer. The prompt is not "
			+ "approved.";

	private final String responseText;

	public InvalidNovaResponseException(String responseText) {
		super("A result without problems must preserve the complete user input", MSG_INCOMPLETE, null,
				INVALID_RESPONSE);
		this.responseText = responseText == null ? "" : responseText;
	}

	/** The model's unchecked output. Built from the already redacted input, returned only to the sender. */
	public String responseText() {
		return responseText;
	}
}
