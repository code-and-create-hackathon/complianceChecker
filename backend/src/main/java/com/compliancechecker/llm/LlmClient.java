package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import org.springframework.stereotype.Component;

/**
 * Asks the LLM whether a prompt complies with the given rules. The only class that knows which LLM is used.
 */
@Component
public class LlmClient {

	/**
	 * @throws LlmException if the model cannot be reached or returns an unusable answer
	 */
	public CheckResponse check(String prompt, String complianceRules) {
		// Amazon Bedrock is connected in step 3.
		throw new LlmException("No LLM configured yet");
	}

	/** Identifier of the model in use, e.g. the Bedrock model id. */
	public String modelId() {
		return "none";
	}
}
