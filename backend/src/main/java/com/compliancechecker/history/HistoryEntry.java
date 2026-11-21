package com.compliancechecker.history;

import com.compliancechecker.model.Severity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One past compliance check. Deliberately has no field for the original prompt or for the matched passages
 * ({@code location}); see {@link HistoryService}. Also used directly as the JSON body of {@code GET /api/history}.
 */
@Entity
@Table(name = "check_history")
public class HistoryEntry {

	static final int MAX_IMPROVED_VERSION_LENGTH = 25_000;
	static final int MAX_FINDINGS_LENGTH = 10_000;

	/** Rule id and severity of one problem; message and location are never stored. */
	public record Finding(String ruleId, Severity severity) {
	}

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private boolean safe;

	/** "ruleId:severity" pairs, comma-separated; a plain column keeps the schema portable to PostgreSQL. */
	@Column(nullable = false, length = MAX_FINDINGS_LENGTH)
	private String findings;

	/** Sanitised prompt, or "" if there is none or it could not be stored safely. */
	@Column(nullable = false, length = MAX_IMPROVED_VERSION_LENGTH)
	private String improvedVersion;

	protected HistoryEntry() {
		// for JPA
	}

	/** Rule ids must already be safe ids (see {@code Problem.safeRuleId()}): no ":" or ",". */
	HistoryEntry(Instant createdAt, boolean safe, List<Finding> findings, String improvedVersion) {
		this.createdAt = createdAt;
		this.safe = safe;
		this.findings = join(findings);
		this.improvedVersion = improvedVersion;
	}

	public Long getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@JsonProperty("isSafe")
	public boolean isSafe() {
		return safe;
	}

	public List<Finding> getFindings() {
		if (findings.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(findings.split(","))
				.map(pair -> pair.split(":"))
				.map(parts -> new Finding(parts[0], Severity.valueOf(parts[1].toUpperCase(Locale.ROOT))))
				.toList();
	}

	public String getImprovedVersion() {
		return improvedVersion;
	}

	/** Joins as many findings as fit into the column. */
	private static String join(List<Finding> findings) {
		StringBuilder joined = new StringBuilder();
		for (Finding finding : findings) {
			String pair = finding.ruleId() + ":" + finding.severity().jsonValue();
			if (joined.length() + pair.length() + 1 > MAX_FINDINGS_LENGTH) {
				break;
			}
			joined.append(joined.isEmpty() ? "" : ",").append(pair);
		}
		return joined.toString();
	}
}
