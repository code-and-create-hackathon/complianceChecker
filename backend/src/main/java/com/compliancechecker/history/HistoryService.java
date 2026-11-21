package com.compliancechecker.history;

import com.compliancechecker.history.HistoryEntry.Finding;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.service.SensitiveDataScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Stores and lists past checks. Never stores the original prompt or matched passages: only time, result, rule ids with
 * severities and the sanitised prompt, and the sanitised prompt only if it passes {@link #storableImprovedVersion}.
 */
@Service
public class HistoryService {

	private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

	/** Shorter passages (e.g. "R") would also match harmless text. */
	private static final int MIN_CHECKED_LOCATION_LENGTH = 3;

	private final HistoryRepository repository;
	private final SensitiveDataScanner scanner;
	private final Clock clock;

	@Autowired
	public HistoryService(HistoryRepository repository, SensitiveDataScanner scanner) {
		this(repository, scanner, Clock.systemUTC());
	}

	HistoryService(HistoryRepository repository, SensitiveDataScanner scanner, Clock clock) {
		this.repository = repository;
		this.scanner = scanner;
		this.clock = clock;
	}

	/**
	 * Saves a check. The original prompt is only used in memory to make sure it is not stored. A failure to save
	 * is logged and does not fail the check itself.
	 */
	public void record(String originalPrompt, CheckResponse result) {
		List<Finding> findings = result.problems().stream()
				.map(problem -> new Finding(problem.safeRuleId(), problem.severity()))
				.toList();
		HistoryEntry entry = new HistoryEntry(Instant.now(clock), result.isSafe(), findings,
				storableImprovedVersion(originalPrompt, result));
		try {
			repository.save(entry);
		}
		catch (DataAccessException ex) {
			log.warn("history entry not saved exception={}", ex.getClass().getSimpleName());
		}
	}

	public List<HistoryEntry> latestEntries() {
		return repository.findTop100ByOrderByCreatedAtDescIdDesc();
	}

	/**
	 * Returns the sanitised prompt if it is safe to store, otherwise "". It is not stored if it
	 * <ul>
	 * <li>is identical to the original prompt (nothing was sanitised, so it would be the original),</li>
	 * <li>still contains a matched passage ({@code location}) of one of the problems,</li>
	 * <li>still contains sensitive data the scanner can find, or</li>
	 * <li>is too long for the column.</li>
	 * </ul>
	 */
	String storableImprovedVersion(String originalPrompt, CheckResponse result) {
		String improvedVersion = result.improvedVersion();
		boolean containsMatchedPassage = result.problems().stream()
				.map(Problem::location)
				.filter(location -> location.strip().length() >= MIN_CHECKED_LOCATION_LENGTH)
				.anyMatch(improvedVersion::contains);
		if (improvedVersion.isBlank()
				|| improvedVersion.strip().equals(originalPrompt.strip())
				|| containsMatchedPassage
				|| improvedVersion.length() > HistoryEntry.MAX_IMPROVED_VERSION_LENGTH
				|| scanner.scan(improvedVersion).foundSensitiveData()) {
			return "";
		}
		return improvedVersion;
	}
}
