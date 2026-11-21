package com.compliancechecker.controller;

import com.compliancechecker.history.HistoryService;
import com.compliancechecker.llm.LlmClient;
import com.compliancechecker.llm.LlmException;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Finding;
import com.compliancechecker.model.ProblemCategory;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
	void returnsResultInApiShape() throws Exception {
		given(llmClient.check(anyString(), anyString())).willReturn(new CheckResponse(false,
				List.of("Contains an IBAN"), List.of(ProblemCategory.ACCOUNT_DATA), "Transfer from [IBAN]", true,
				List.of(new Finding("R-02", "critical", "Contains an IBAN", SECRET))));

		postCheck(json("Transfer from " + SECRET, "# Rules\nNo IBANs."))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isSafe").value(false))
				.andExpect(jsonPath("$.problems[0].ruleId").value("R-02"))
				.andExpect(jsonPath("$.problems[0].location").value(SECRET))
				.andExpect(jsonPath("$.improvedVersion").value("Transfer from [IBAN]"))
				.andExpect(jsonPath("$.simulated").doesNotExist());
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
				.willReturn(new CheckResponse(true, List.of(), List.of(), "", true));

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
				.andExpect(jsonPath("$.error").value("LLM_INVALID_RESPONSE"))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(SECRET);
		assertThat(output.getAll()).doesNotContain(SECRET);
	}

	@Test
	void reportsBedrockAccessDeniedWithAwsMessage() throws Exception {
		var aws = software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException.builder()
				.message(SECRET).statusCode(403).build();
		given(llmClient.check(anyString(), anyString())).willThrow(new LlmException("Bedrock failed", aws));
		String body = postCheck(json("Example prompt", "rules"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("LLM_ACCESS_DENIED"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains(SECRET);
	}

	@Test
	void logsOnlyMetadata(CapturedOutput output) throws Exception {
		given(llmClient.check(anyString(), anyString()))
				.willReturn(new CheckResponse(true, List.of(), List.of(), "harmless", true));

		postCheck(json("Note " + SECRET, "rules")).andExpect(status().isOk());

		assertThat(output.getAll()).contains("safe=true").doesNotContain(SECRET);
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
