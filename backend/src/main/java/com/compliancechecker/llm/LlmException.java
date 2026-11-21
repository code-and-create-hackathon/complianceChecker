package com.compliancechecker.llm;

/**
 * The LLM call failed. The message must never contain prompt or rule content.
 *
 * <p>{@link #userMessage()} is a fixed text that may be shown to the user (for example "AWS credentials are missing").
 * It never comes from the cause's message, which could echo request details.
 */
public class LlmException extends RuntimeException {

	static final String DEFAULT_USER_MESSAGE = "The compliance check is temporarily unavailable. Please try again.";

	private final String userMessage;

	public LlmException(String message) {
		this(message, DEFAULT_USER_MESSAGE, null);
	}

	public LlmException(String message, Throwable cause) {
		this(message, DEFAULT_USER_MESSAGE, cause);
	}

	public LlmException(String message, String userMessage, Throwable cause) {
		super(message, cause);
		this.userMessage = userMessage;
	}

	/** Fixed, user-safe explanation of what went wrong. */
	public String userMessage() {
		return userMessage;
	}
}
