package com.compliancechecker.service;

import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SanitiserTest {

	@Test
	void widensAPartlyMarkedValueWithOddSpacing() {
		String text = "Pls write - his iban is D E 8 9 3704-0044 0532 0130 00 and loan 25.000 EUR.";

		assertThat(Sanitiser.widen(text, "0044 0532 0130 00")).isEqualTo("D E 8 9 3704-0044 0532 0130 00");
	}

	@Test
	void doesNotWidenOverWordsOrShortPassages() {
		String text = "The IBAN DE89 3704 0044 is here, call me Max.";

		assertThat(Sanitiser.widen(text, "3704 0044")).isEqualTo("DE89 3704 0044");
		assertThat(Sanitiser.widen(text, "Max")).isEqualTo("Max");
	}

	@Test
	void replacesOnlyTheReportedPassagesAndKeepsEverythingElse() {
		String text = "Write to max musterman about 25,000 EUR; max musterman agreed.";
		List<Problem> problems = List.of(
				new Problem("R-01", "Personal data of customers", Severity.CRITICAL, "Name.", "max musterman"));

		assertThat(Sanitiser.replacePassages(text, problems))
				.isEqualTo("Write to [PERSONAL_DATA_OF_CUSTOMERS_1] about 25,000 EUR; [PERSONAL_DATA_OF_CUSTOMERS_1] agreed.");
	}

	@Test
	void numbersDifferentValuesPerCategoryAndHandlesMissingCategories() {
		String text = "Anna and Ben, code 1234";
		List<Problem> problems = List.of(
				new Problem("R-01", "Clients", Severity.CRITICAL, "Name.", "Anna"),
				new Problem("R-01", "Clients", Severity.CRITICAL, "Name.", "Ben"),
				new Problem("R-05", Severity.CRITICAL, "Code.", "1234"));

		assertThat(Sanitiser.replacePassages(text, problems)).isEqualTo("[CLIENTS_1] and [CLIENTS_2], code [REMOVED_1]");
	}

	@Test
	void dropsDuplicatesAndPassagelessEntriesOfAnAlreadyReportedRule() {
		List<Problem> problems = List.of(
				new Problem("R-02", Severity.CRITICAL, "IBAN.", "DE89"),
				new Problem("R-02", Severity.CRITICAL, "IBAN again.", ""),
				new Problem("R-02", Severity.CRITICAL, "IBAN.", "DE89"),
				new Problem("R-07", Severity.HIGH, "No passage, but the only R-07 finding.", ""));

		assertThat(Sanitiser.deduplicate(problems)).extracting(Problem::ruleId, Problem::location).containsExactly(
				org.assertj.core.groups.Tuple.tuple("R-02", "DE89"),
				org.assertj.core.groups.Tuple.tuple("R-07", ""));
	}
}
