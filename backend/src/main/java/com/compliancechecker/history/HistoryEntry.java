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
 * One past compliance check: only non-sensitive metadata. Deliberately has no field for any prompt text (original or
 * sanitised: a pattern scanner cannot know every name in it), for the matched passages ({@code location}) or for the
 * messages. Also used directly as the JSON body of {@code GET /api/history}.
 *
 * <p>The table is called {@code compliance_check}: databases created by earlier versions have a {@code check_history}
 * table with a mandatory text column, which new entries could not fill.
 */
@Entity
@Table(name = "compliance_check")
public class HistoryEntry {

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

	protected HistoryEntry() {
		// for JPA
	}

	/** Rule ids must already be safe ids (see {@code Problem.safeRuleId()}): no ":" or ",". */
	HistoryEntry(Instant createdAt, boolean safe, List<Finding> findings) {
		this.createdAt = createdAt;
		this.safe = safe;
		this.findings = join(findings);
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
