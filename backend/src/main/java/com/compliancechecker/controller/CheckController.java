package com.compliancechecker.controller;

import com.compliancechecker.account.AppUser;
import com.compliancechecker.account.AuthInterceptor;
import com.compliancechecker.model.CheckRequest;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.service.ComplianceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckController {

	private final ComplianceService complianceService;

	public CheckController(ComplianceService complianceService) {
		this.complianceService = complianceService;
	}

	@PostMapping("/api/check")
	public CheckResponse check(@Valid @RequestBody CheckRequest request,
			@RequestAttribute(AuthInterceptor.CURRENT_USER) AppUser user) {
		return complianceService.check(request.prompt(), user);
	}
}
