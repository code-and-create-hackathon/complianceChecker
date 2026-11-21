package com.compliancechecker.history;

import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.service.SensitiveDataScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end without AWS using a test-only live-shaped LLM response and an in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class HistoryIntegrationTest {

	private static final String EMAIL = "erika.mustermann@example.com";
	private static final String SAFE_PROMPT = "Explain the difference between ETFs and mutual funds.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HistoryRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SensitiveDataScanner scanner;

	@MockitoBean
	private LlmClient llmClient;

	@BeforeEach
	void emptyHistory() {
		repository.deleteAll();
		given(llmClient.check(anyString(), anyString())).willAnswer(invocation -> {
			String prompt = invocation.getArgument(0);
			var scan = scanner.scan(prompt);
			return new CheckResponse(!scan.foundSensitiveData(), scan.problems(), scan.categories(),
					scan.redactedText(), false, scanner.findings(prompt));
		});
	}

	@Test
	void checkIsRecordedAndListedNewestFirst() throws Exception {
		check(SAFE_PROMPT);
		check("Reply to " + EMAIL + " about her loan.");

		mockMvc.perform(get("/api/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].isSafe").value(false))
				.andExpect(jsonPath("$[0].categories[0]").value("CUSTOMER_DATA"))
				.andExpect(jsonPath("$[0].improvedVersion").value("Reply to [EMAIL] about her loan."))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$[1].isSafe").value(true))
				.andExpect(jsonPath("$[1].improvedVersion").isEmpty());
	}

	@Test
	void originalPromptIsNeverStoredOrLogged(CapturedOutput output) throws Exception {
		check("Reply to " + EMAIL + " about her loan.");
		check("Call +49 30 1234567. The merger with Example AG is strictly confidential.");
		check(SAFE_PROMPT);

		String allStoredValues = jdbcTemplate.queryForList("SELECT * FROM history_entry").stream()
				.map(Map::values)
				.flatMap(java.util.Collection::stream)
				.map(String::valueOf)
				.reduce("", String::concat);

		for (String sensitive : List.of(EMAIL, "1234567", "Example AG", SAFE_PROMPT)) {
			assertThat(allStoredValues).doesNotContain(sensitive);
			assertThat(output.getAll()).doesNotContain(sensitive);
		}
	}

	@Test
	void checkStillWorksAsBefore() throws Exception {
		check(SAFE_PROMPT)
				.andExpect(jsonPath("$.isSafe").value(true))
				.andExpect(jsonPath("$.improvedVersion").value(SAFE_PROMPT));
	}

	private org.springframework.test.web.servlet.ResultActions check(String prompt) throws Exception {
		String body = "{\"prompt\":\"%s\",\"compliance\":\"# Rules\"}".formatted(prompt);
		return mockMvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
	}
}
