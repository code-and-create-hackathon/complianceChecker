package com.compliancechecker.history;

import com.compliancechecker.history.HistoryEntry.Finding;
import com.compliancechecker.model.CheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Stores and lists past checks. Stores only non-sensitive metadata: time, result and the rule ids with severities.
 * Never any prompt text (original or sanitised), matched passages or messages.
 */
@Service
public class HistoryService {

	private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

	private final HistoryRepository repository;
	private final Clock clock;

	@Autowired
	public HistoryService(HistoryRepository repository) {
		this(repository, Clock.systemUTC());
	}

	HistoryService(HistoryRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** Saves a check. A failure to save is logged and does not fail the check itself. */
	public void record(CheckResponse result) {
		List<Finding> findings = result.problems().stream()
				.map(problem -> new Finding(problem.safeRuleId(), problem.severity()))
				.toList();
		try {
			repository.save(new HistoryEntry(Instant.now(clock), result.isSafe(), findings));
		}
		catch (DataAccessException ex) {
			log.warn("history entry not saved exception={}", ex.getClass().getSimpleName());
		}
	}

	public List<HistoryEntry> latestEntries() {
		return repository.findTop100ByOrderByCreatedAtDescIdDesc();
	}
}
