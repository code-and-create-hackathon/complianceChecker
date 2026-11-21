package com.compliancechecker.llm;

/**
 * The LLM call failed. The message must never contain prompt or rule content.
 */
public class LlmException extends RuntimeException {

	public LlmException(String message) {
		super(message);
	}

	public LlmException(String message, Throwable cause) {
		super(message, cause);
	}
}
