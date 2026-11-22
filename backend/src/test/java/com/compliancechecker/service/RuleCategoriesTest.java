package com.compliancechecker.service;

import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCategoriesTest {

	private static final String RULES = """
			# Policy
			### R-01 Personal data of customers (CRITICAL)
			- **X-7:** Data of private clients – severity: high
			R-1 Short id that must not match R-10
			| R-10 | Project names | low |
			""";

	@Test
	void takesTheNameOfTheRuleFromItsHeading() {
		assertThat(RuleCategories.categoryFor(RULES, "R-01")).isEqualTo("Personal data of customers");
	}

	@Test
	void worksWithOtherIdsListsAndTables() {
		assertThat(RuleCategories.categoryFor(RULES, "X-7")).isEqualTo("Data of private clients");
		assertThat(RuleCategories.categoryFor(RULES, "R-10")).isEqualTo("Project names");
	}

	@Test
	void unknownRuleHasNoCategory() {
		assertThat(RuleCategories.categoryFor(RULES, "R-99")).isEmpty();
		assertThat(RuleCategories.categoryFor(null, "R-01")).isEmpty();
	}

	@Test
	void fillsOnlyMissingCategories() {
		List<Problem> filled = RuleCategories.fillMissing(List.of(
				new Problem("R-01", Severity.CRITICAL, "Name.", "Erika"),
				new Problem("R-01", "Own category", Severity.CRITICAL, "Name.", "Max")), RULES);

		assertThat(filled).extracting(Problem::category).containsExactly("Personal data of customers", "Own category");
	}
}
