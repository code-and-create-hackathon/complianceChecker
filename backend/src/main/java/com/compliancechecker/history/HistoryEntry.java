package com.compliancechecker.history;

import com.compliancechecker.model.ProblemCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One past compliance check. Deliberately has no field for the original prompt; see {@link HistoryService}.
 * Also used directly as the JSON body of {@code GET /api/history}.
 */
@Entity
@Table(name = "history_entry")
public class HistoryEntry {

	static final int MAX_IMPROVED_VERSION_LENGTH = 25_000;

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private boolean safe;

	/** Comma-separated {@link ProblemCategory} names; a plain column keeps the schema portable to PostgreSQL. */
	@Column(nullable = false)
	private String categories;

	/** Sanitized prompt, or null if it could not be stored safely. */
	@Column(length = MAX_IMPROVED_VERSION_LENGTH)
	private String improvedVersion;

	protected HistoryEntry() {
		// for JPA
	}

	HistoryEntry(Instant createdAt, boolean safe, List<ProblemCategory> categories, String improvedVersion) {
		this.createdAt = createdAt;
		this.safe = safe;
		this.categories = categories.stream().map(Enum::name).collect(Collectors.joining(","));
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

	public List<ProblemCategory> getCategories() {
		if (categories.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(categories.split(",")).map(ProblemCategory::valueOf).toList();
	}

	public String getImprovedVersion() {
		return improvedVersion;
	}
}
