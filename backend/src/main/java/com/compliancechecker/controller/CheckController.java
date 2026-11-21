package com.compliancechecker.controller;

import com.compliancechecker.model.CheckRequest;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.ApiCheckResponse;
import com.compliancechecker.service.ComplianceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckController {

	private final ComplianceService complianceService;

	public CheckController(ComplianceService complianceService) {
		this.complianceService = complianceService;
	}

	@PostMapping("/api/check")
	public ApiCheckResponse check(@Valid @RequestBody CheckRequest request) {
		CheckResponse result = complianceService.check(request.prompt(), request.compliance());
		return new ApiCheckResponse(result.isSafe(), result.findings(), result.improvedVersion());
	}
}
