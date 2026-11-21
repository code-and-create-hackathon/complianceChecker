package com.compliancechecker.service;

import com.compliancechecker.history.HistoryService;
import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.llm.LlmException;
import com.compliancechecker.model.CheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checks prompts against compliance rules and records the result in the history. Logs only metadata, never content.
 */
@Service
public class ComplianceService {

	private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

	private final LlmClient llmClient;
	private final HistoryService historyService;

	public ComplianceService(LlmClient llmClient, HistoryService historyService) {
		this.llmClient = llmClient;
		this.historyService = historyService;
	}

	public CheckResponse check(String prompt, String complianceRules) {
		long startNanos = System.nanoTime();
		CheckResponse result = llmClient.check(prompt, complianceRules);
		if (result.simulated()) {
			throw new LlmException("Simulated compliance result rejected by the live API.");
		}
		long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

		log.info("check promptChars={} rulesChars={} safe={} categories={} model={} latencyMs={}",
				prompt.length(), complianceRules.length(), result.isSafe(), result.categories(),
				llmClient.modelId(), latencyMs);
		historyService.record(prompt, result);
		return result;
	}
}
