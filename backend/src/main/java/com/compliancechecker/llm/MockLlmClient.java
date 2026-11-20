package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.service.SensitiveDataScanner;
import com.compliancechecker.service.SensitiveDataScanner.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulated compliance check for local development and demos without AWS, based on {@link SensitiveDataScanner}.
 *
 * <p>This is not a real compliance check: it ignores the uploaded compliance rules and only finds the scanner's
 * patterns. Every result is marked with {@code simulated = true}.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

	private final SensitiveDataScanner scanner;

	public MockLlmClient(SensitiveDataScanner scanner) {
		this.scanner = scanner;
		log.warn("LLM provider 'mock' is active: results are simulated, no AWS calls are made.");
	}

	@Override
	public CheckResponse check(String prompt, String complianceRules) {
		ScanResult scan = scanner.scan(prompt);
		return new CheckResponse(!scan.foundSensitiveData(), scan.problems(), scan.categories(), scan.redactedText(),
				true);
	}

	@Override
	public String modelId() {
		return "mock";
	}
}
