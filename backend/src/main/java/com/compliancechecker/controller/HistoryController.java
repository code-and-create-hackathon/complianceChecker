package com.compliancechecker.controller;

import com.compliancechecker.account.AppUser;
import com.compliancechecker.account.AuthInterceptor;
import com.compliancechecker.history.HistoryEntry;
import com.compliancechecker.history.HistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HistoryController {

	private final HistoryService historyService;

	public HistoryController(HistoryService historyService) {
		this.historyService = historyService;
	}

	/** The latest 100 checks, newest first: the administrator sees all users, a worker only their own. */
	@GetMapping("/api/history")
	public List<HistoryEntry> history(@RequestAttribute(AuthInterceptor.CURRENT_USER) AppUser user) {
		return historyService.latestEntries(user);
	}
}
