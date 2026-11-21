package com.compliancechecker.controller;

import com.compliancechecker.history.HistoryService;
import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.llm.LlmException;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import com.compliancechecker.service.ComplianceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs without AWS: the LLM is replaced by a Mockito mock. */
@WebMvcTest(CheckController.class)
@Import(ComplianceService.class)
@ExtendWith(OutputCaptureExtension.class)
class CheckControllerTest {

	/** Made-up IBAN; it must never show up in responses or logs. */
	private static final String SECRET = "DE89370400440532013000";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LlmClient llmClient;

	@MockitoBean
	private HistoryService historyService;

	@Test
	void returnsExactlyTheApiMdResponseShape() throws Exception {
		given(llmClient.check(anyString(), anyString())).willReturn(new CheckResponse(
				List.of(new Problem("R-02", Severity.CRITICAL, "Contains an IBAN.", SECRET)), "Transfer from [IBAN_1]"));

		// STRICT: no extra or missing fields, as API.md requires.
		postCheck(json("Transfer from " + SECRET, "# Rules\nNo IBANs."))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "isSafe": false,
						  "problems": [
						    {"ruleId": "R-02", "severity": "critical", "message": "Contains an IBAN.", "location": "%s"}
						  ],
						  "improvedVersion": "Transfer from [IBAN_1]"
						}
						""".formatted(SECRET), JsonCompareMode.STRICT));
	}

	@Test
	void lowSeverityProblemKeepsPromptSafe() throws Exception {
		given(llmClient.check(anyString(), anyString())).willReturn(new CheckResponse(
				List.of(new Problem("R-10", Severity.LOW, "Consider removing the project name.", "Project X")), ""));

		postCheck(json("Status of Project X?", "rules"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isSafe").value(true))
				.andExpect(jsonPath("$.problems[0].severity").value("low"));
	}

	@Test
	void rejectsEmptyFields() throws Exception {
		postCheck(json(" ", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.fieldErrors.prompt").value("must not be empty"))
				.andExpect(jsonPath("$.fieldErrors.compliance").value("must not be empty"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void rejectsMissingField() throws Exception {
		postCheck("{\"compliance\":\"rules\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.prompt").value("must not be empty"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void rejectsTooLongPrompt() throws Exception {
		postCheck(json("x".repeat(20_001), "rules"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.prompt").value("is too long (max. 20000 characters)"));
		verifyNoInteractions(llmClient);
	}

	@Test
	void acceptsComplianceRulesUpTo500000Characters() throws Exception {
		given(llmClient.check(anyString(), anyString()))
				.willReturn(new CheckResponse(List.of(), ""));

		postCheck(json("Explain ETFs.", "r".repeat(500_000))).andExpect(status().isOk());
		postCheck(json("Explain ETFs.", "r".repeat(500_001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.compliance").value("is too long (max. 500000 characters)"));
	}

	@Test
	void rejectsMissingBody() throws Exception {
		mockMvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("The request body is missing or is not valid JSON."));
	}

	@Test
	void rejectsNonJsonContentType() throws Exception {
		mockMvc.perform(post("/api/check").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void rejectsWrongHttpMethod() throws Exception {
		mockMvc.perform(get("/api/check"))
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
	void llmFailureReturns502WithoutLeakingContent(CapturedOutput output) throws Exception {
		given(llmClient.check(anyString(), anyString())).willThrow(new LlmException("Bedrock timeout"));

		String body = postCheck(json("Transfer from " + SECRET, "rules"))
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

		postCheck(json("Note " + SECRET, "rules")).andExpect(status().isOk());

		assertThat(output.getAll()).contains("safe=false", "R-02").doesNotContain(SECRET);
	}

	private ResultActions postCheck(String body) throws Exception {
		return mockMvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String json(String prompt, String compliance) {
		return "{\"prompt\":\"%s\",\"compliance\":\"%s\"}".formatted(escape(prompt), escape(compliance));
	}

	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}
}
