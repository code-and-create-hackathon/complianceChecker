package com.compliancechecker.history;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubmittedPromptRepository extends JpaRepository<SubmittedPrompt, Long> {
	List<SubmittedPrompt> findTop100ByOrderByCreatedAtDescIdDesc();
}
