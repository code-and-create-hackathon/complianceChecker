package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.ProblemCategory;
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
	private static final String RULES = "# Rules\n- Never share customer names or IBANs with external AI systems.";

	private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
	private final BedrockLlmClient llmClient = new BedrockLlmClient(bedrock, MODEL_ID);

	@Test
	void mapsToolResultToCheckResponse() {
		givenBedrockAnswers(toolResult(false, List.of("Contains a customer name", "Contains an IBAN"),
				List.of("CUSTOMER_DATA", "ACCOUNT_DATA"), "Please summarise the account of [CUSTOMER_NAME], IBAN [IBAN]."));

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).containsExactly("Contains a customer name", "Contains an IBAN");
		assertThat(result.categories()).containsExactly(ProblemCategory.CUSTOMER_DATA, ProblemCategory.ACCOUNT_DATA);
		assertThat(result.improvedVersion()).isEqualTo("Please summarise the account of [CUSTOMER_NAME], IBAN [IBAN].");
	}

	@Test
	void sendsPromptAsDataAndRulesInSystemPrompt() {
		givenBedrockAnswers(toolResult(true, List.of(), List.of(), "harmless"));

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
	void safeResultWithProblemsIsNotSafe() {
		givenBedrockAnswers(toolResult(true, List.of("Contains an IBAN"), List.of("ACCOUNT_DATA"), "..."));

		assertThat(llmClient.check(PROMPT, RULES).isSafe()).isFalse();
	}

	@Test
	void unknownCategoryBecomesOther() {
		givenBedrockAnswers(toolResult(false, List.of("Something else"), List.of("SOMETHING_NEW"), "..."));

		assertThat(llmClient.check(PROMPT, RULES).categories()).containsExactly(ProblemCategory.OTHER);
	}

	@Test
	void failsWhenModelAnswersWithTextInsteadOfTool() {
		givenBedrockAnswers(response(ContentBlock.fromText("Looks fine to me!"), StopReason.END_TURN));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	@Test
	void failsWhenResultFieldIsMissing() {
		Map<String, Document> incomplete = new HashMap<>(toolInput(true, List.of(), List.of(), "x").asMap());
		incomplete.remove("isSafe");
		givenBedrockAnswers(toolUse(Document.fromMap(incomplete)));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	@Test
	void failsWhenOutputWasCutOff() {
		givenBedrockAnswers(response(ContentBlock.fromText("{\"isSafe\": tr"), StopReason.MAX_TOKENS));

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

	private void givenBedrockAnswers(ConverseResponse response) {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(response);
	}

	private static ConverseResponse toolResult(boolean isSafe, List<String> problems, List<String> categories,
			String improvedVersion) {
		return toolUse(toolInput(isSafe, problems, categories, improvedVersion));
	}

	private static Document toolInput(boolean isSafe, List<String> problems, List<String> categories,
			String improvedVersion) {
		return Document.fromMap(Map.of(
				"isSafe", Document.fromBoolean(isSafe),
				"problems", strings(problems),
				"categories", strings(categories),
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

	private static Document strings(List<String> values) {
		return Document.fromList(values.stream().map(Document::fromString).toList());
	}
}
