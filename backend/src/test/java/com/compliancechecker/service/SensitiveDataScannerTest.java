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

	@Test
	void luhnCheck() {
		assertThat(SensitiveDataScanner.passesLuhnCheck("4111 1111 1111 1111")).isTrue();
		assertThat(SensitiveDataScanner.passesLuhnCheck("1234 5678 9012 3456")).isFalse();
	}
}
