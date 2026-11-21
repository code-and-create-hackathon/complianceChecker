package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import com.compliancechecker.service.SensitiveDataScanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** All test data is made up: example.com addresses, fictitious numbers and the standard test card number. */
class MockLlmClientTest {

	private static final String RULES = "# Rules\n- No personal data.";

	private final MockLlmClient mock = new MockLlmClient(new SensitiveDataScanner());

	@Test
	void harmlessPromptIsSafeWithoutSuggestion() {
		CheckResponse result = mock.check("Summarise the differences between ETFs and actively managed funds.", RULES);

		assertThat(result.isSafe()).isTrue();
		assertThat(result.problems()).isEmpty();
		assertThat(result.improvedVersion()).isEmpty();
	}

	@Test
	void detectsEmailAddress() {
		CheckResponse result = mock.check("Write a reply to erika.mustermann@example.com about her loan.", RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).containsExactly(
				new Problem("R-01", Severity.CRITICAL, "Contains an email address.", "erika.mustermann@example.com"));
		assertThat(result.improvedVersion()).isEqualTo("Write a reply to [EMAIL_1] about her loan.");
	}

	@Test
	void reportsEveryMatchSeparatelyWithNumberedPlaceholders() {
		CheckResponse result = mock.check("Call +49 30 1234567 or 0171-7654321 tomorrow.", RULES);

		assertThat(result.problems()).extracting(Problem::location).containsExactly("+49 30 1234567", "0171-7654321");
		assertThat(result.problems()).extracting(Problem::ruleId).containsOnly("R-01");
		assertThat(result.improvedVersion()).isEqualTo("Call [PHONE_1] or [PHONE_2] tomorrow.");
	}

	@Test
	void detectsCreditCardNumber() {
		CheckResponse result = mock.check("Why was card 4111 1111 1111 1111 declined?", RULES);

		assertThat(result.problems()).containsExactly(
				new Problem("R-02", Severity.CRITICAL, "Contains a credit card number.", "4111 1111 1111 1111"));
		assertThat(result.improvedVersion()).isEqualTo("Why was card [CARD_NUMBER_1] declined?");
	}

	@Test
	void ignoresDigitSequencesThatAreNoCardNumber() {
		assertThat(mock.check("Order 1234 5678 9012 3456 was shipped.", RULES).isSafe()).isTrue();
	}

	@Test
	void detectsIban() {
		CheckResponse result = mock.check("Transfer 100 EUR to DE89 3704 0044 0532 0130 00.", RULES);

		assertThat(result.problems()).extracting(Problem::ruleId, Problem::location)
				.containsExactly(tuple("R-02", "DE89 3704 0044 0532 0130 00"));
		assertThat(result.improvedVersion()).isEqualTo("Transfer 100 EUR to [IBAN_1].");
	}

	@Test
	void confidentialInformationIsMediumAndBlocks() {
		CheckResponse result = mock.check(
				"Improve my email. The merger with Example AG is strictly confidential. Keep it short.", RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).containsExactly(new Problem("R-09", Severity.MEDIUM,
				"Contains information marked as confidential.", "The merger with Example AG is strictly confidential."));
		// The whole sentence goes, so the company name does not stay behind.
		assertThat(result.improvedVersion()).isEqualTo("Improve my email. [REMOVED_1] Keep it short.");
	}

	@Test
	void confidentialSentenceIsNotSplitByDotsInsideEmailAddresses() {
		CheckResponse result = mock.check("Ask a.b@example.com, this is confidential. Thanks.", RULES);

		assertThat(result.problems()).extracting(Problem::ruleId).containsExactly("R-09");
		assertThat(result.improvedVersion()).isEqualTo("[REMOVED_1] Thanks.");
	}

	@Test
	void detectsGermanConfidentialityKeyword() {
		CheckResponse result = mock.check("Die Quartalszahlen sind noch nicht öffentlich.", RULES);

		assertThat(result.problems()).extracting(Problem::location)
				.containsExactly("Die Quartalszahlen sind noch nicht öffentlich.");
	}

	@Test
	void listsProblemsInOrderOfAppearance() {
		CheckResponse result = mock.check(
				"Mail a@example.com and b@example.com, call 030 1234567, card 4111-1111-1111-1111.", RULES);

		assertThat(result.problems()).extracting(Problem::location)
				.containsExactly("a@example.com", "b@example.com", "030 1234567", "4111-1111-1111-1111");
		assertThat(result.improvedVersion())
				.isEqualTo("Mail [EMAIL_1] and [EMAIL_2], call [PHONE_1], card [CARD_NUMBER_1].");
	}

	@Test
	void doesNotTreatAmountsOrDatesAsPhoneNumbers() {
		assertThat(mock.check("Invest 25000 EUR on 2026-11-21 at 3.5 % interest.", RULES).isSafe()).isTrue();
	}

	@Test
	void locationIsAlwaysVerbatimFromThePrompt() {
		String prompt = "Mail erika@example.com. IBAN DE89 3704 0044 0532 0130 00. This is internal only.";

		assertThat(mock.check(prompt, RULES).problems())
				.hasSize(3)
				.allSatisfy(problem -> assertThat(prompt).contains(problem.location()));
	}
}
