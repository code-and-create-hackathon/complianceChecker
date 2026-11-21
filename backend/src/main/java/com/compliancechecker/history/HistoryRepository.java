package com.compliancechecker.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoryRepository extends JpaRepository<HistoryEntry, Long> {

	List<HistoryEntry> findTop100ByOrderByCreatedAtDescIdDesc();
}
