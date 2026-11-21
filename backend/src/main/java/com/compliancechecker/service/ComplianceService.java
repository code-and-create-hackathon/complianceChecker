package com.compliancechecker.service;

import com.compliancechecker.history.HistoryService;
import com.compliancechecker.llm.LlmClient;
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
		long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

		// Problem.toString() shows only rule id and severity, never message or location.
		log.info("check promptChars={} rulesChars={} safe={} problems={} model={} latencyMs={}",
				prompt.length(), complianceRules.length(), result.isSafe(), result.problems(),
				llmClient.modelId(), latencyMs);
		historyService.record(prompt, result);
		return result;
	}
}
