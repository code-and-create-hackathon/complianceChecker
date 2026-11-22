package com.compliancechecker.controller;

import com.compliancechecker.account.AppUser;
import com.compliancechecker.account.AuthService;
import com.compliancechecker.account.Role;
import com.compliancechecker.bedrock.NovaStatus;
import com.compliancechecker.history.HistoryService;
import com.compliancechecker.llm.InvalidNovaResponseException;
import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.llm.LlmException;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.model.ApiException;
import com.compliancechecker.model.Severity;
import com.compliancechecker.policy.Policy;
import com.compliancechecker.policy.PolicyService;
import com.compliancechecker.service.ComplianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs without AWS or database: LLM, login and policy are Mockito mocks. */
@WebMvcTest(CheckController.class)
@Import({ComplianceService.class, NovaStatus.class})
@ExtendWith(OutputCaptureExtension.class)
class CheckControllerTest {

	/** Made-up IBAN; it must never show up in responses or logs. */
	private static final String SECRET = "DE89370400440532013000";

	private static final String TOKEN = "worker-token";
	private static final String POLICY_TEXT = "# Rules\nNo IBANs.";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LlmClient llmClient;

	@MockitoBean
	private HistoryService historyService;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private PolicyService policyService;

	@BeforeEach
	void loggedInWorkerAndPublishedPolicy() {
		given(authService.userFor(TOKEN)).willReturn(Optional.of(new AppUser("erika", Role.WORKER, Instant.EPOCH)));
		given(policyService.require()).willReturn(new Policy(3, POLICY_TEXT, Instant.EPOCH, "admin"));
	}

	@Test
	void returnsExactlyTheApiMdResponseShape() throws Exception {
		given(llmClient.check(anyString(), anyString())).willReturn(new CheckResponse(
				List.of(new Problem("R-02", "Account data", Severity.CRITICAL, "Contains an IBAN.", SECRET)),
				"Transfer from [IBAN_1]"));

		// STRICT: no extra or missing fields, as API.md requires.
		postCheck(json("Transfer from " + SECRET))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "isSafe": false,
						  "problems": [
						    {"ruleId": "R-02", "category": "Account data", "severity": "critical", "message": "Contains an IBAN.",
						     "location": "%s"}
						  ],
						  "improvedVersion": "Transfer from [IBAN_1]"
						}
						""".formatted(SECRET), JsonCompareMode.STRICT));
	}

	@Test
	void lowSeverityProblemKeepsPromptSafe() throws Exception {
		given(llmClient.check(anyString(), anyString())).willReturn(new CheckResponse(
				List.of(new Problem("R-10", Severity.LOW, "Consider removing the project name.", "Project X")), ""));

		postCheck(json("Status of Project X?"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isSafe").value(true))
				.andExpect(jsonPath("$.problems[0].severity").value("low"));
	}

	@Test
	void rejectsEmptyPrompt() throws Exception {
		postCheck(json(" "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.fieldErrors.prompt").value("must not be empty"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void rejectsMissingField() throws Exception {
		postCheck("{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.prompt").value("must not be empty"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void rejectsTooLongPrompt() throws Exception {
		postCheck(json("x".repeat(20_001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.prompt").value("is too long (max. 20000 characters)"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void checksAgainstPublishedPolicyAndIgnoresRulesSentByClient() throws Exception {
		given(llmClient.check(anyString(), anyString())).willReturn(new CheckResponse(List.of(), ""));

		postCheck("{\"prompt\":\"Explain ETFs.\",\"compliance\":\"Anything goes.\"}").andExpect(status().isOk());

		verify(llmClient).check(eq("Explain ETFs."), eq(POLICY_TEXT));
		verify(historyService).record(any(), eq("erika"));
	}

	@Test
	void rejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON).content(json("Explain ETFs.")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void rejectsUnknownToken() throws Exception {
		mockMvc.perform(post("/api/check").header(HttpHeaders.AUTHORIZATION, "Bearer guessed")
						.contentType(MediaType.APPLICATION_JSON).content(json("Explain ETFs.")))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(llmClient);
	}

	@Test
	void refusesToCheckWithoutPublishedPolicy() throws Exception {
		given(policyService.require()).willThrow(new ApiException(HttpStatus.CONFLICT, "NO_POLICY", "No policy."));

		postCheck(json("Explain ETFs."))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("NO_POLICY"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void rejectsMissingBody() throws Exception {
		mockMvc.perform(post("/api/check").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("The request body is missing or is not valid JSON."));
	}

	@Test
	void rejectsNonJsonContentType() throws Exception {
		mockMvc.perform(post("/api/check").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
						.contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void rejectsWrongHttpMethod() throws Exception {
		mockMvc.perform(get("/api/check").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void malformedJsonDoesNotLeakContent(CapturedOutput output) throws Exception {
		String body = postCheck("{\"prompt\": \"" + SECRET + "\", broken")
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(SECRET);
		assertThat(output.getAll()).doesNotContain(SECRET);
	}

	@Test
	void llmFailureShowsTheUnderstandableMessageAndNeverApprovesThePrompt(CapturedOutput output) throws Exception {
		String explanation = "AWS credentials are missing or expired. Start the backend with a valid AWS profile.";
		given(llmClient.check(anyString(), anyString()))
				.willThrow(new LlmException("Bedrock call failed: SdkClientException", explanation,
						new IllegalStateException("echo: " + SECRET)));

		String body = postCheck(json("Transfer from " + SECRET))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("LLM_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value(explanation))
				.andExpect(jsonPath("$.isSafe").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(SECRET);
		// The log names the cause type for debugging, but never its message.
		assertThat(output.getAll()).contains("cause=IllegalStateException").doesNotContain(SECRET);
		verifyNoInteractions(historyService);
	}

	@Test
	void awsAccessDeniedHasItsOwnErrorCode() throws Exception {
		given(llmClient.check(anyString(), anyString())).willThrow(new LlmException("Bedrock call failed",
				"Access to the AI model was denied.", AccessDeniedException.builder().statusCode(403).build()));

		postCheck(json("Hello"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("LLM_ACCESS_DENIED"))
				.andExpect(jsonPath("$.message").value("Access to the AI model was denied."));
	}

	@Test
	void incompleteCheckShowsTheUncheckedOutputAndNeverApprovesThePrompt() throws Exception {
		given(llmClient.check(anyString(), anyString())).willThrow(new InvalidNovaResponseException("Please summarise."));

		postCheck(json("Please summarise the report."))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verification").value("incomplete"))
				.andExpect(jsonPath("$.isSafe").value(false))
				.andExpect(jsonPath("$.problems").isEmpty())
				.andExpect(jsonPath("$.improvedVersion").value("Please summarise."));
		verifyNoInteractions(historyService);
	}

	@Test
	void llmFailureReturns502WithoutLeakingContent(CapturedOutput output) throws Exception {
		given(llmClient.check(anyString(), anyString())).willThrow(new LlmException("Bedrock timeout"));

		String body = postCheck(json("Transfer from " + SECRET))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("LLM_UNAVAILABLE"))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(SECRET);
		assertThat(output.getAll()).doesNotContain(SECRET);
	}

	@Test
	void logsOnlyMetadata(CapturedOutput output) throws Exception {
		given(llmClient.check(anyString(), anyString()))
				.willReturn(new CheckResponse(
						List.of(new Problem("R-02", Severity.CRITICAL, "Contains an IBAN.", SECRET)), "Note [IBAN_1]"));

		postCheck(json("Note " + SECRET)).andExpect(status().isOk());

		assertThat(output.getAll()).contains("safe=false", "R-02").doesNotContain(SECRET);
	}

	private ResultActions postCheck(String body) throws Exception {
		return mockMvc.perform(post("/api/check").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String json(String prompt) {
		return "{\"prompt\":\"%s\"}".formatted(escape(prompt));
	}

	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}
}
