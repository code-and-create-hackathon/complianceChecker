package com.compliancechecker.service;

import com.compliancechecker.account.AppUser;
import com.compliancechecker.history.HistoryService;
import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.policy.Policy;
import com.compliancechecker.policy.PolicyService;
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
	private final PolicyService policyService;

	public ComplianceService(LlmClient llmClient, HistoryService historyService, PolicyService policyService) {
		this.llmClient = llmClient;
		this.historyService = historyService;
		this.policyService = policyService;
	}

	/**
	 * Checks the prompt against the active company policy, never against rules sent by the client.
	 *
	 * @throws com.compliancechecker.model.ApiException 409 {@code NO_POLICY} if no policy is published yet
	 */
	public CheckResponse check(String prompt, AppUser user) {
		Policy policy = policyService.require();
		String complianceRules = policy.getText();
		long startNanos = System.nanoTime();
		CheckResponse result = llmClient.check(prompt, complianceRules);
		long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

		// Problem.toString() shows only rule id and severity, never message or location.
		log.info("check userId={} policyVersion={} promptChars={} rulesChars={} safe={} problems={} model={} latencyMs={}",
				user.getId(), policy.getVersion(), prompt.length(), complianceRules.length(), result.isSafe(),
				result.problems(), llmClient.modelId(), latencyMs);
		historyService.record(result, user.getUsername());
		return result;
	}
}
