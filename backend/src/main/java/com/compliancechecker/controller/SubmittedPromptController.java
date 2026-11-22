package com.compliancechecker.controller;

import com.compliancechecker.account.AdminOnly;
import com.compliancechecker.history.SubmittedPrompt;
import com.compliancechecker.history.SubmittedPromptRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class SubmittedPromptController {
	private final SubmittedPromptRepository prompts;

	public SubmittedPromptController(SubmittedPromptRepository prompts) {
		this.prompts = prompts;
	}

	/** Administrator only: the stored prompts contain the original, unredacted text. */
	@AdminOnly
	@GetMapping("/api/prompts")
	public List<SubmittedPrompt> latest() {
		return prompts.findTop100ByOrderByCreatedAtDescIdDesc();
	}
}
