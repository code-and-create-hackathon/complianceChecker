package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;

/**
 * Checks a prompt against compliance rules. Selected with {@code llm.provider}: {@code bedrock} (default) or
 * {@code mock} (offline demo, no AWS).
 */
public interface LlmClient {

	/**
	 * @throws LlmException if the check cannot be completed; the prompt must then not be approved
	 */
	CheckResponse check(String prompt, String complianceRules);

	/** Identifier of the model in use, for logs and history. */
	String modelId();
}
