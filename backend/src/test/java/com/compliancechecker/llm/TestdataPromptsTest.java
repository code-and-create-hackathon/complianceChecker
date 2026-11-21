package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.service.SensitiveDataScanner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo fallback must handle the team's showcase prompts from {@code Testdata/test-prompts.md} (all fictional).
 * Reads the file directly, so changes to the test data show up here.
 */
class TestdataPromptsTest {

	private static final Path TEST_PROMPTS = Path.of("..", "Testdata", "test-prompts.md");
	private static final Map<String, String> PROMPTS = new HashMap<>();

	private final SensitiveDataScanner scanner = new SensitiveDataScanner();
	private final MockLlmClient mock = new MockLlmClient(scanner);

	@BeforeAll
	static void readTestPrompts() throws IOException {
		Matcher matcher = Pattern.compile("\\*\\*(T\\d+)[^*]*\\*\\*\\n> (.+)").matcher(Files.readString(TEST_PROMPTS));
		while (matcher.find()) {
			PROMPTS.put(matcher.group(1), matcher.group(2));
		}
		assertThat(PROMPTS).hasSize(9);
	}

	@ParameterizedTest(name = "{0} passes")
	@CsvSource({ "T1", "T2", "T3", "T9" })
	void allowedPromptsPass(String id) {
		CheckResponse result = mock.check(PROMPTS.get(id), "");

		assertThat(result.isSafe()).isTrue();
		assertThat(result.problems()).isEmpty();
	}

	@ParameterizedTest(name = "{0} is blocked with {1}")
	@CsvSource(delimiter = ';', value = {
			"T4; R-01 R-02; Thomas Albrecht|14.03.1971|Lindenstraße 12|70173|DE89",
			"T5; R-08; Nordhafen Projekt GmbH",
			"T6; R-04; profit warning|Vantra",
			"T7; R-05; Sommer2026!|10.20.4.17",
			"T8; R-03; Julia Brenner|depression" })
	void violatingPromptsAreBlockedAndSanitised(String id, String expectedRuleIds, String sensitiveValues) {
		String prompt = PROMPTS.get(id);

		CheckResponse result = mock.check(prompt, "");

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).extracting(Problem::ruleId).contains(expectedRuleIds.split(" "));
		assertThat(result.problems()).allSatisfy(problem -> assertThat(prompt).contains(problem.location()));
		for (String value : sensitiveValues.split("\\|")) {
			assertThat(result.improvedVersion()).doesNotContain(value);
		}
		// The sanitised version is clean enough to be stored in the history.
		assertThat(scanner.scan(result.improvedVersion()).foundSensitiveData()).isFalse();
	}
}
