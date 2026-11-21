package com.compliancechecker.bedrock;

import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.model.CheckResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "nova")
class NovaComplianceClient implements LlmClient {

	private final BedrockNovaClient nova;

	NovaComplianceClient(BedrockNovaClient nova) {
		this.nova = nova;
	}

	@Override
	public CheckResponse check(String prompt, String rules) {
		return nova.check(prompt, rules);
	}

	@Override
	public String modelId() {
		return "nova-micro";
	}
}
