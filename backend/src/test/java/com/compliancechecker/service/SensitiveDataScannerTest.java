package com.compliancechecker.service;

import com.compliancechecker.service.SensitiveDataScanner.ScanResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Detection cases are covered in MockLlmClientTest; this covers what the history relies on. All data is made up. */
class SensitiveDataScannerTest {

	private final SensitiveDataScanner scanner = new SensitiveDataScanner();

	@Test
	void redactedTextContainsNoFurtherFindings() {
		ScanResult first = scanner.scan("Mail erika.mustermann@example.com, call +49 30 1234567, card 4111 1111 1111 1111, "
				+ "IBAN DE89 3704 0044 0532 0130 00. The merger is confidential.");

		assertThat(first.problems()).hasSize(5);
		// Placeholders must not trigger the rules again, otherwise the history could never store sanitised text.
		assertThat(scanner.scan(first.redactedText()).foundSensitiveData()).isFalse();
	}

	@Test
	void overlappingMatchesAreReportedOnce() {
		// The IBAN contains digit groups that alone could look like a phone number.
		ScanResult result = scanner.scan("IBAN DE89 3704 0044 0532 0130 00");

		assertThat(result.problems()).hasSize(1);
		assertThat(result.redactedText()).isEqualTo("IBAN [IBAN_1]");
	}

	/** Regression: sentences of a few thousand characters used to end in a StackOverflowError (HTTP 500). */
	@Test
	void longTextWithoutSentenceEndIsScannedWithoutError() {
		String text = "lorem ipsum ".repeat(1_667); // about the maximum prompt length (20 000 characters)

		ScanResult result = scanner.scan(text);

		assertThat(result.foundSensitiveData()).isFalse();
		assertThat(result.redactedText()).isEqualTo(text);
	}

	@Test
	void longTextWithManyDotsInsideWordsIsScannedWithoutError() {
		// Dots that are not followed by whitespace do not end a sentence (file names, version numbers, code).
		String text = "see file.name.ext and 1.2.3 ".repeat(700);

		assertThat(scanner.scan(text).foundSensitiveData()).isFalse();
	}

	@Test
	void veryLongSentenceWithKeywordIsRemovedAsAWhole() {
		String sentence = "A ".repeat(6_000) + "this is confidential.";

		ScanResult result = scanner.scan(sentence + " Thanks.");

		assertThat(result.problems()).hasSize(1);
		assertThat(result.problems().getFirst().location()).isEqualTo(sentence);
		assertThat(result.redactedText()).isEqualTo("[REMOVED_1] Thanks.");
	}

	@Test
	void severalKeywordsInOneSentenceAreReportedOnce() {
		ScanResult result = scanner.scan("This confidential memo is not public. Done.");

		assertThat(result.problems()).hasSize(1);
		assertThat(result.redactedText()).isEqualTo("[REMOVED_1] Done.");
	}

	@Test
	void luhnCheck() {
		assertThat(SensitiveDataScanner.passesLuhnCheck("4111 1111 1111 1111")).isTrue();
		assertThat(SensitiveDataScanner.passesLuhnCheck("1234 5678 9012 3456")).isFalse();
	}
}
