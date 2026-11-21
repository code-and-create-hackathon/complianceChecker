package com.compliancechecker.controller;

import com.compliancechecker.history.HistoryEntry;
import com.compliancechecker.history.HistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HistoryController {

	private final HistoryService historyService;

	public HistoryController(HistoryService historyService) {
		this.historyService = historyService;
	}

	/** The latest 100 checks, newest first. */
	@GetMapping("/api/history")
	public List<HistoryEntry> history() {
		return historyService.latestEntries();
	}
}
