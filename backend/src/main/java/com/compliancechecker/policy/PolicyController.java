package com.compliancechecker.policy;

import com.compliancechecker.account.AdminOnly;
import com.compliancechecker.account.AppUser;
import com.compliancechecker.account.AuthInterceptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Everyone logged in may read the active policy (workers should know the rules); only the admin may change it. */
@RestController
public class PolicyController {

	private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

	public record PolicyRequest(
			@NotBlank(message = "must not be empty")
			@Size(max = Policy.MAX_TEXT_LENGTH, message = "is too long (max. {max} characters)")
			String text) {

		@Override
		public String toString() {
			return "PolicyRequest[text=%d chars]".formatted(text == null ? 0 : text.length());
		}
	}

	public record PolicyView(int version, String text, Instant publishedAt, String publishedBy) {

		static PolicyView of(Policy policy) {
			return new PolicyView(policy.getVersion(), policy.getText(), policy.getPublishedAt(),
					policy.getPublishedBy());
		}
	}

	private final PolicyService policyService;

	public PolicyController(PolicyService policyService) {
		this.policyService = policyService;
	}

	@GetMapping("/api/policy")
	public PolicyView current() {
		return PolicyView.of(policyService.require());
	}

	@AdminOnly
	@PutMapping("/api/policy")
	public PolicyView publish(@Valid @RequestBody PolicyRequest request,
			@RequestAttribute(AuthInterceptor.CURRENT_USER) AppUser admin) {
		Policy policy = policyService.publish(request.text(), admin);
		log.info("policy published version={} chars={}", policy.getVersion(), policy.getText().length());
		return PolicyView.of(policy);
	}
}
