package com.compliancechecker.llm;

public class InvalidNovaResponseException extends LlmException {
	private final String responseText;

	public InvalidNovaResponseException(String responseText) {
		super("A conforming result must preserve the complete user input");
		this.responseText = responseText;
	}

	public String responseText() {
		return responseText;
	}
}
