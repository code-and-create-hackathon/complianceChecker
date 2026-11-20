package com.compliancechecker.service;

import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.model.CheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checks prompts against compliance rules. Logs only metadata, never content.
 */
@Service
public class ComplianceService {

	private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

	private final LlmClient llmClient;

	public ComplianceService(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	public CheckResponse check(String prompt, String complianceRules) {
		long startNanos = System.nanoTime();
		CheckResponse result = llmClient.check(prompt, complianceRules);
		long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

		log.info("check promptChars={} rulesChars={} safe={} categories={} model={} latencyMs={}",
				prompt.length(), complianceRules.length(), result.isSafe(), result.categories(),
				llmClient.modelId(), latencyMs);
		return result;
	}
}
