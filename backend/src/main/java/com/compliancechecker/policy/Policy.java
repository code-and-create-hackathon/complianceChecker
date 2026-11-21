package com.compliancechecker.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One published version of the company's compliance policy. Versions are never changed or deleted, so the table is
 * also the audit trail of who published what and when. The highest version is the active one.
 */
@Entity
@Table(name = "compliance_policy")
public class Policy {

	public static final int MAX_TEXT_LENGTH = 500_000;

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false, unique = true)
	private int version;

	/** The rules as one Markdown string (several files concatenated as in API.md). */
	@Column(nullable = false, length = MAX_TEXT_LENGTH)
	private String text;

	@Column(nullable = false)
	private Instant publishedAt;

	/** Username of the administrator who published it. */
	@Column(nullable = false)
	private String publishedBy;

	protected Policy() {
		// for JPA
	}

	public Policy(int version, String text, Instant publishedAt, String publishedBy) {
		this.version = version;
		this.text = text;
		this.publishedAt = publishedAt;
		this.publishedBy = publishedBy;
	}

	public int getVersion() {
		return version;
	}

	public String getText() {
		return text;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public String getPublishedBy() {
		return publishedBy;
	}
}
