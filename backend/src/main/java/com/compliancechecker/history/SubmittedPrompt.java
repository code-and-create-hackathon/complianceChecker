package com.compliancechecker.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class SubmittedPrompt {

	@Id @GeneratedValue
	private Long id;
	@Column(nullable = false)
	private Instant createdAt;
	@Column(nullable = false, length = 1000000)
	private String prompt;

	protected SubmittedPrompt() { }

	public SubmittedPrompt(String prompt) {
		this.createdAt = Instant.now();
		this.prompt = prompt;
	}

	public Long getId() { return id; }
	public Instant getCreatedAt() { return createdAt; }
	public String getPrompt() { return prompt; }
}
