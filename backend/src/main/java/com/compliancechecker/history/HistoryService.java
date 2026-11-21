package com.compliancechecker.history;

import com.compliancechecker.model.CheckResponse;
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
 * Stores and lists past checks. This history stores time, result, categories and the
 * sanitized prompt, and the sanitized prompt only if it passes {@link #storableImprovedVersion}.
 */
@Service
public class HistoryService {

	private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

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
		String improvedVersion = storableImprovedVersion(originalPrompt, result.improvedVersion());
		HistoryEntry entry = new HistoryEntry(Instant.now(clock), result.isSafe(), result.categories(),
				improvedVersion);
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
	 * Returns the sanitized prompt if it is safe to store, otherwise null. It is not stored if it
	 * <ul>
	 * <li>is identical to the original prompt (nothing was sanitized, so it would be the original),</li>
	 * <li>still contains sensitive data the scanner can find, or</li>
	 * <li>is too long for the column.</li>
	 * </ul>
	 */
	String storableImprovedVersion(String originalPrompt, String improvedVersion) {
		if (improvedVersion == null || improvedVersion.isBlank()
				|| improvedVersion.strip().equals(originalPrompt.strip())
				|| improvedVersion.length() > HistoryEntry.MAX_IMPROVED_VERSION_LENGTH
				|| scanner.scan(improvedVersion).foundSensitiveData()) {
			return null;
		}
		return improvedVersion;
	}
}
