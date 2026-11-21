package com.compliancechecker.policy;

import com.compliancechecker.account.AppUser;
import com.compliancechecker.model.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** The one compliance policy all checks run against. Only the administrator publishes it (see PolicyController). */
@Service
public class PolicyService {

	private final PolicyRepository repository;
	private final Clock clock;

	@Autowired
	public PolicyService(PolicyRepository repository) {
		this(repository, Clock.systemUTC());
	}

	PolicyService(PolicyRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public Optional<Policy> current() {
		return repository.findTopByOrderByVersionDesc();
	}

	/** @throws ApiException 409 {@code NO_POLICY} if the administrator has not published one yet */
	public Policy require() {
		return current().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_POLICY",
				"No compliance policy has been published yet. Please ask your administrator."));
	}

	/** Stores the text as the new active version. The unique version column rejects a parallel publish. */
	@Transactional
	public Policy publish(String text, AppUser admin) {
		int nextVersion = current().map(Policy::getVersion).orElse(0) + 1;
		return repository.save(new Policy(nextVersion, text, Instant.now(clock), admin.getUsername()));
	}
}
