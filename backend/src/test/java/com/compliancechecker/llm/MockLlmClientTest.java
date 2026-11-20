package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.ProblemCategory;
import com.compliancechecker.service.SensitiveDataScanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** All test data is made up: example.com addresses, fictitious numbers and the standard test card number. */
class MockLlmClientTest {

	private static final String RULES = "# Rules\n- No personal data.";

	private final MockLlmClient mock = new MockLlmClient(new SensitiveDataScanner());

	@Test
	void harmlessPromptIsSafe() {
		String prompt = "Summarise the main differences between ETFs and actively managed funds in 2026.";

		CheckResponse result = mock.check(prompt, RULES);

		assertThat(result.isSafe()).isTrue();
		assertThat(result.problems()).isEmpty();
		assertThat(result.categories()).isEmpty();
		assertThat(result.improvedVersion()).isEqualTo(prompt);
		assertThat(result.simulated()).isTrue();
	}

	@Test
	void detectsEmailAddress() {
		CheckResponse result = mock.check("Write a reply to erika.mustermann@example.com about her loan.", RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).containsExactly("Contains an email address.");
		assertThat(result.categories()).containsExactly(ProblemCategory.CUSTOMER_DATA);
		assertThat(result.improvedVersion()).isEqualTo("Write a reply to [EMAIL] about her loan.");
	}

	@Test
	void detectsPhoneNumbers() {
		CheckResponse result = mock.check("Call +49 30 1234567 or 0171-7654321 tomorrow.", RULES);

		assertThat(result.problems()).containsExactly("Contains a phone number.");
		assertThat(result.improvedVersion()).isEqualTo("Call [PHONE] or [PHONE] tomorrow.");
	}

	@Test
	void detectsCreditCardNumber() {
		CheckResponse result = mock.check("Why was card 4111 1111 1111 1111 declined?", RULES);

		assertThat(result.problems()).containsExactly("Contains a credit card number.");
		assertThat(result.categories()).containsExactly(ProblemCategory.ACCOUNT_DATA);
		assertThat(result.improvedVersion()).isEqualTo("Why was card [CARD_NUMBER] declined?");
	}

	@Test
	void ignoresDigitSequencesThatAreNoCardNumber() {
		CheckResponse result = mock.check("Order 1234 5678 9012 3456 was shipped.", RULES);

		assertThat(result.isSafe()).isTrue();
	}

	@Test
	void detectsIban() {
		CheckResponse result = mock.check("Transfer 100 EUR to DE89 3704 0044 0532 0130 00.", RULES);

		assertThat(result.problems()).containsExactly("Contains an IBAN.");
		assertThat(result.improvedVersion()).isEqualTo("Transfer 100 EUR to [IBAN].");
	}

	@Test
	void removesSentenceWithConfidentialInformation() {
		CheckResponse result = mock.check(
				"Improve my email. The merger with Example AG is strictly confidential. Keep it short.", RULES);

		assertThat(result.problems()).containsExactly("Contains confidential company information.");
		assertThat(result.categories()).containsExactly(ProblemCategory.INSIDER_INFO);
		assertThat(result.improvedVersion())
				.isEqualTo("Improve my email. [SENSITIVE SENTENCE REMOVED] Keep it short.");
	}

	@Test
	void detectsGermanConfidentialityKeyword() {
		CheckResponse result = mock.check("Die Quartalszahlen sind noch nicht öffentlich.", RULES);

		assertThat(result.categories()).containsExactly(ProblemCategory.INSIDER_INFO);
	}

	@Test
	void reportsEveryProblemOnceAndCategoriesWithoutDuplicates() {
		CheckResponse result = mock.check(
				"Mail a@example.com and b@example.com, call 030 1234567, card 4111-1111-1111-1111.", RULES);

		assertThat(result.problems()).containsExactly(
				"Contains an email address.", "Contains a credit card number.", "Contains a phone number.");
		assertThat(result.categories()).containsExactly(ProblemCategory.CUSTOMER_DATA, ProblemCategory.ACCOUNT_DATA);
		assertThat(result.improvedVersion()).isEqualTo("Mail [EMAIL] and [EMAIL], call [PHONE], card [CARD_NUMBER].");
	}

	@Test
	void doesNotTreatAmountsOrDatesAsPhoneNumbers() {
		assertThat(mock.check("Invest 25000 EUR on 2026-11-21 at 3.5 % interest.", RULES).isSafe()).isTrue();
	}
}
