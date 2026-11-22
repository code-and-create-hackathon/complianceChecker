package com.compliancechecker.bedrock;

import org.springframework.stereotype.Component;

/** Outcome and duration of the last compliance check, for the live status badge on the website. */
@Component
public class NovaStatus {

	private volatile String lastOutcome = "unverified";
	private volatile Long lastLatencyMs;

	public void record(boolean success, long latencyMs) {
		lastOutcome = success ? "success" : "failed";
		lastLatencyMs = latencyMs;
	}

	String lastOutcome() {
		return lastOutcome;
	}

	Long lastLatencyMs() {
		return lastLatencyMs;
	}
}
