package com.compliancechecker.history;

import com.compliancechecker.TestAccounts;
import com.compliancechecker.account.AuthService;
import com.compliancechecker.account.UserRepository;
import com.compliancechecker.account.UserService;
import com.compliancechecker.policy.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end in mock mode (no AWS) with an in-memory H2 database: POST /api/check as a logged-in worker, then
 * GET /api/history, then a look at the raw database rows. All test data is made up.
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
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthService authService;

	@Autowired
	private PolicyService policyService;

	private String erika;
	private String max;

	@BeforeEach
	void emptyHistoryWithPolicyAndWorkers() {
		repository.deleteAll();
		TestAccounts.publishPolicy(policyService, userRepository, "# Rules");
		erika = TestAccounts.workerAuthorization(userService, authService, "erika");
		max = TestAccounts.workerAuthorization(userService, authService, "max");
	}

	@Test
	void checkIsRecordedAndListedNewestFirst() throws Exception {
		check(SAFE_PROMPT);
		check("Reply to " + EMAIL + " about her loan.");

		mockMvc.perform(get("/api/history").header(HttpHeaders.AUTHORIZATION, erika))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].username").value("erika"))
				.andExpect(jsonPath("$[0].isSafe").value(false))
				.andExpect(jsonPath("$[0].findings[0].ruleId").value("R-01"))
				.andExpect(jsonPath("$[0].findings[0].severity").value("critical"))
				.andExpect(jsonPath("$[0].findings[0].location").doesNotExist())
				.andExpect(jsonPath("$[0].improvedVersion").doesNotExist())
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$[1].isSafe").value(true))
				.andExpect(jsonPath("$[1].findings").isEmpty())
				.andExpect(jsonPath("$[1].improvedVersion").doesNotExist());
	}

	@Test
	void workerSeesOnlyOwnChecksAndAdminSeesAll() throws Exception {
		check(SAFE_PROMPT, erika);
		check(SAFE_PROMPT, max);
		check(SAFE_PROMPT, max);

		mockMvc.perform(get("/api/history").header(HttpHeaders.AUTHORIZATION, erika))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].username").value("erika"));
		mockMvc.perform(get("/api/history").header(HttpHeaders.AUTHORIZATION,
						TestAccounts.adminAuthorization(authService)))
				.andExpect(jsonPath("$.length()").value(3));
		mockMvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
	}

	@Test
	void noPromptTextNamesOrMatchedPassagesAreStoredOrLogged(CapturedOutput output) throws Exception {
		check("Reply to " + EMAIL + " about her loan.");
		// A name without form of address is not detected by any pattern; it must still never reach DB or log.
		check("Customer Thomas Albrecht from Stuttgart asks about a loan. Reply to thomas.a@example.com.");
		// The response contains the matched passages (location), as API.md requires; DB and log must not.
		check("Call +49 30 1234567. The merger with Example AG is strictly confidential.")
				.andExpect(jsonPath("$.problems[0].location").value("+49 30 1234567"));
		check(SAFE_PROMPT);

		String allStoredValues = jdbcTemplate.queryForList("SELECT * FROM compliance_check").stream()
				.map(Map::values)
				.flatMap(java.util.Collection::stream)
				.map(String::valueOf)
				.reduce("", String::concat);

		for (String sensitive : List.of(EMAIL, "1234567", "Example AG", SAFE_PROMPT, "Thomas Albrecht", "Stuttgart",
				"Reply to", "[EMAIL_1]")) {
			assertThat(allStoredValues).doesNotContain(sensitive);
			assertThat(output.getAll()).doesNotContain(sensitive);
		}
	}

	@Test
	void tableHasOnlyMetadataColumns() throws Exception {
		check("Reply to " + EMAIL + " about her loan.");

		Map<String, Object> row = jdbcTemplate.queryForList("SELECT * FROM compliance_check").getFirst();

		assertThat(row.keySet()).containsExactlyInAnyOrder("ID", "CREATED_AT", "USERNAME", "SAFE", "FINDINGS");
	}

	@Test
	void checkAnswersExactlyInApiMdShape() throws Exception {
		check(SAFE_PROMPT)
				.andExpect(content().json("""
						{"isSafe": true, "problems": [], "improvedVersion": ""}
						""", JsonCompareMode.STRICT));
	}

	private org.springframework.test.web.servlet.ResultActions check(String prompt) throws Exception {
		return check(prompt, erika);
	}

	private org.springframework.test.web.servlet.ResultActions check(String prompt, String authorization)
			throws Exception {
		String body = "{\"prompt\":\"%s\"}".formatted(prompt);
		return mockMvc.perform(post("/api/check").header(HttpHeaders.AUTHORIZATION, authorization)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
	}
}
