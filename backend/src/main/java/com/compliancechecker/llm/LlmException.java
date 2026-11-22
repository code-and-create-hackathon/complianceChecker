package com.compliancechecker.llm;

/**
 * The LLM call failed. The message must never contain prompt or rule content.
 *
 * <p>{@link #userMessage()} is a fixed text that may be shown to the user (for example "AWS credentials are missing").
 * It never comes from the cause's message, which could echo request details.
 *
 * <p>{@link #code()} is the error code of the API response (API.md). AWS errors get their code from the cause in
 * {@code ApiExceptionHandler}.
 */
public class LlmException extends RuntimeException {

	static final String DEFAULT_USER_MESSAGE = "The compliance check is temporarily unavailable. Please try again.";

	public static final String UNAVAILABLE = "LLM_UNAVAILABLE";
	public static final String INVALID_RESPONSE = "LLM_INVALID_RESPONSE";
	public static final String NOT_CONNECTED = "LLM_NOT_CONNECTED";

	private final String userMessage;
	private final String code;

	public LlmException(String message) {
		this(message, DEFAULT_USER_MESSAGE, null);
	}

	public LlmException(String message, Throwable cause) {
		this(message, DEFAULT_USER_MESSAGE, cause);
	}

	public LlmException(String message, String userMessage, Throwable cause) {
		this(message, userMessage, cause, UNAVAILABLE);
	}

	public LlmException(String message, String userMessage, Throwable cause, String code) {
		super(message, cause);
		this.userMessage = userMessage;
		this.code = code;
	}

	/** Fixed, user-safe explanation of what went wrong. */
	public String userMessage() {
		return userMessage;
	}

	/** Error code for the API response, e.g. {@value #INVALID_RESPONSE}. */
	public String code() {
		return code;
	}
}
