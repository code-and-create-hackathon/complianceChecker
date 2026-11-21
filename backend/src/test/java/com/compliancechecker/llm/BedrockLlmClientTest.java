package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Uses a mocked Bedrock client; no AWS access, no costs. All test data is made up. */
class BedrockLlmClientTest {

	private static final String MODEL_ID = "eu.amazon.nova-2-lite-v1:0";
	private static final String PROMPT = "Please summarise the account of Erika Mustermann, IBAN DE89370400440532013000.";
	private static final String RULES = "# Rules\n### R-01 Customer names (CRITICAL)\n### R-02 IBANs (CRITICAL)";

	private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
	private final BedrockLlmClient llmClient = new BedrockLlmClient(bedrock, MODEL_ID);

	@Test
	void mapsToolResultToApiMdProblems() {
		givenBedrockAnswers(false,
				List.of(problem("R-01", "critical", "Contains a customer name.", "Erika Mustermann"),
						problem("R-02", "critical", "Contains an IBAN.", "DE89370400440532013000")),
				"Please summarise the account of [CUSTOMER_NAME_1], IBAN [IBAN_1].");

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).containsExactly(
				new Problem("R-01", Severity.CRITICAL, "Contains a customer name.", "Erika Mustermann"),
				new Problem("R-02", Severity.CRITICAL, "Contains an IBAN.", "DE89370400440532013000"));
		assertThat(result.improvedVersion()).isEqualTo("Please summarise the account of [CUSTOMER_NAME_1], IBAN [IBAN_1].");
	}

	@Test
	void sendsPromptAsDataAndRulesInSystemPrompt() {
		givenBedrockAnswers(true, List.of(), "");

		llmClient.check(PROMPT, RULES);

		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock).converse(captor.capture());
		ConverseRequest request = captor.getValue();
		assertThat(request.modelId()).isEqualTo(MODEL_ID);
		assertThat(request.system().getFirst().text()).contains(RULES).contains(BedrockLlmClient.TOOL_NAME);
		assertThat(request.messages().getFirst().content().getFirst().text())
				.isEqualTo("<prompt_to_check>\n" + PROMPT + "\n</prompt_to_check>");
		assertThat(request.toolConfig().tools().getFirst().toolSpec().name()).isEqualTo(BedrockLlmClient.TOOL_NAME);
	}

	@Test
	void isSafeFollowsSeveritiesNotTheModelFlag() {
		givenBedrockAnswers(true, List.of(problem("R-02", "critical", "Contains an IBAN.", "")), "...");

		assertThat(llmClient.check(PROMPT, RULES).isSafe()).isFalse();
	}

	@Test
	void lowSeverityOnlyIsSafe() {
		givenBedrockAnswers(true, List.of(problem("R-10", "low", "Consider shortening.", "")), "");

		assertThat(llmClient.check(PROMPT, RULES).isSafe()).isTrue();
	}

	@Test
	void safeResultWithoutProblemsHasNoSuggestion() {
		givenBedrockAnswers(true, List.of(), "the original prompt again");

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isTrue();
		assertThat(result.improvedVersion()).isEmpty();
	}

	@Test
	void unsafeVerdictWithoutBlockingProblemGetsUnspecifiedCriticalProblem() {
		givenBedrockAnswers(false, List.of(problem("R-10", "low", "Just a notice.", "")), "...");

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).contains(new Problem(Problem.UNSPECIFIED_RULE, Severity.CRITICAL,
				BedrockLlmClient.UNSPECIFIED_MESSAGE, ""));
	}

	@Test
	void unknownSeverityCountsAsCriticalAndBlankRuleIdBecomesUnspecified() {
		givenBedrockAnswers(true, List.of(problem(" ", "severe", "Something.", "")), "...");

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems().getFirst().ruleId()).isEqualTo(Problem.UNSPECIFIED_RULE);
		assertThat(result.problems().getFirst().severity()).isEqualTo(Severity.CRITICAL);
	}

	@Test
	void locationNotFoundVerbatimInPromptIsDropped() {
		givenBedrockAnswers(false, List.of(problem("R-01", "critical", "Contains a name.", "Mrs Mustermann")), "...");

		assertThat(llmClient.check(PROMPT, RULES).problems().getFirst().location()).isEmpty();
	}

	@Test
	void failsWhenModelAnswersWithTextInsteadOfTool() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willReturn(response(ContentBlock.fromText("Looks fine to me!"), StopReason.END_TURN));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	@Test
	void failsWhenResultFieldIsMissing() {
		Map<String, Document> incomplete = new HashMap<>(toolInput(true, List.of(), "x").asMap());
		incomplete.remove("isSafe");
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(toolUse(Document.fromMap(incomplete)));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	@Test
	void failsWhenProblemFieldIsMissing() {
		Document withoutLocation = Document.fromMap(Map.of(
				"ruleId", Document.fromString("R-01"),
				"severity", Document.fromString("critical"),
				"message", Document.fromString("Contains a name.")));
		givenBedrockAnswers(false, List.of(withoutLocation), "...");

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	@Test
	void failsWhenOutputWasCutOff() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willReturn(response(ContentBlock.fromText("{\"isSafe\": tr"), StopReason.MAX_TOKENS));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	@Test
	void wrapsBedrockErrorsWithoutContent() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willThrow(ValidationException.builder().message("echo: " + PROMPT).build());

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES))
				.isInstanceOf(LlmException.class)
				.hasMessage("Bedrock call failed: ValidationException")
				.message().doesNotContain("Mustermann");
	}

	@Test
	void wrapsNetworkAndCredentialErrors() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willThrow(SdkClientException.create("Unable to load credentials"));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES))
				.isInstanceOf(LlmException.class)
				.hasMessage("Bedrock call failed: SdkClientException");
	}

	private void givenBedrockAnswers(boolean isSafe, List<Document> problems, String improvedVersion) {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(toolUse(toolInput(isSafe, problems, improvedVersion)));
	}

	private static Document problem(String ruleId, String severity, String message, String location) {
		return Document.fromMap(Map.of(
				"ruleId", Document.fromString(ruleId),
				"severity", Document.fromString(severity),
				"message", Document.fromString(message),
				"location", Document.fromString(location)));
	}

	private static Document toolInput(boolean isSafe, List<Document> problems, String improvedVersion) {
		return Document.fromMap(Map.of(
				"isSafe", Document.fromBoolean(isSafe),
				"problems", Document.fromList(problems),
				"improvedVersion", Document.fromString(improvedVersion)));
	}

	private static ConverseResponse toolUse(Document input) {
		ToolUseBlock toolUse = ToolUseBlock.builder().toolUseId("tool-1").name(BedrockLlmClient.TOOL_NAME).input(input).build();
		return response(ContentBlock.fromToolUse(toolUse), StopReason.TOOL_USE);
	}

	private static ConverseResponse response(ContentBlock content, StopReason stopReason) {
		Message message = Message.builder().role(ConversationRole.ASSISTANT).content(content).build();
		return ConverseResponse.builder().output(ConverseOutput.fromMessage(message)).stopReason(stopReason).build();
	}
}
