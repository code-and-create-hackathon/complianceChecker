package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import com.compliancechecker.service.SensitiveDataScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Uses a mocked Bedrock client; no AWS access, no costs. All test data is made up. */
class BedrockLlmClientTest {

	private static final String MODEL_ID = "eu.amazon.nova-2-lite-v1:0";
	/** Nothing in here is found by the local rules, so the model sees the text unchanged. */
	private static final String PROMPT = "Please summarise the account of Erika Mustermann.";
	private static final String PROMPT_WITH_IBAN = PROMPT.replace(".", ", IBAN DE89 3704 0044 0532 0130 00.");
	/** What Bedrock sees of PROMPT_WITH_IBAN after the local scan. */
	private static final String MASKED_PROMPT_WITH_IBAN = "Please summarise the account of Erika Mustermann, IBAN [IBAN_1].";
	private static final String RULES = "# Rules\n### R-01 Customer names (CRITICAL)\n### R-02 IBANs (CRITICAL)";

	private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
	private final BedrockLlmClient llmClient = new BedrockLlmClient(bedrock, new SensitiveDataScanner(), MODEL_ID);

	@Test
	void mapsToolResultToApiMdProblems() {
		givenBedrockAnswers(false, List.of(problem("R-01", "critical", "Contains a customer name.", "Erika Mustermann")),
				"Please summarise the account of [CUSTOMER_NAME_1].");

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).containsExactly(
				new Problem("R-01", "Customer names", Severity.CRITICAL, "Contains a customer name.", "Erika Mustermann"));
		// Built from the reported passage with the category from the rules, not taken over from the model.
		assertThat(result.improvedVersion()).isEqualTo("Please summarise the account of [CUSTOMER_NAMES_1].");
	}

	@Test
	void sendsOnlyThePlaceholderMaskedPromptAndTheRulesToBedrock() {
		givenBedrockAnswers(true, List.of(), MASKED_PROMPT_WITH_IBAN);

		llmClient.check(PROMPT_WITH_IBAN, RULES);

		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock).converse(captor.capture());
		ConverseRequest request = captor.getValue();
		String sentPrompt = request.messages().getFirst().content().getFirst().text();
		assertThat(request.modelId()).isEqualTo(MODEL_ID);
		assertThat(sentPrompt).startsWith("Compliance policies:\n" + RULES + "\nRandom string (delimits the user input):\n");
		// The input stands between two copies of the same random string, each on its own line.
		String marker = sentPrompt.lines().toList().get(sentPrompt.lines().toList().indexOf(MASKED_PROMPT_WITH_IBAN) - 1);
		assertThat(marker).matches("[0-9a-f-]{36}");
		assertThat(sentPrompt).endsWith(marker + "\n" + MASKED_PROMPT_WITH_IBAN + "\n" + marker + "\n");
		assertThat(sentPrompt).doesNotContain("DE89");
		assertThat(request.system().getFirst().text()).contains(BedrockLlmClient.TOOL_NAME)
				.doesNotContain(BedrockLlmClient.RETRY_INSTRUCTION);
		assertThat(request.toolConfig().tools().getFirst().toolSpec().name()).isEqualTo(BedrockLlmClient.TOOL_NAME);
	}

	@Test
	void localFindingsAreAddedToTheModelsFindings() {
		givenBedrockAnswers(false, List.of(problem("R-01", "critical", "Contains a customer name.", "Erika Mustermann")),
				"Please summarise the account of [CUSTOMER_NAME_1], IBAN [IBAN_1].");

		CheckResponse result = llmClient.check(PROMPT_WITH_IBAN, RULES);

		assertThat(result.problems()).extracting(Problem::ruleId, Problem::location).containsExactly(
				org.assertj.core.groups.Tuple.tuple("R-02", "DE89 3704 0044 0532 0130 00"),
				org.assertj.core.groups.Tuple.tuple("R-01", "Erika Mustermann"));
		assertThat(result.improvedVersion()).isEqualTo("Please summarise the account of [CUSTOMER_NAMES_1], IBAN [IBAN_1].");
	}

	@Test
	void usesTheLocallyMaskedTextAsSuggestionWhenTheModelHasNoneOfItsOwn() {
		givenBedrockAnswers(true, List.of(), MASKED_PROMPT_WITH_IBAN);

		CheckResponse result = llmClient.check(PROMPT_WITH_IBAN, RULES);

		assertThat(result.isSafe()).isFalse();
		assertThat(result.problems()).extracting(Problem::ruleId).containsExactly("R-02");
		assertThat(result.improvedVersion()).isEqualTo("Please summarise the account of Erika Mustermann, IBAN [IBAN_1].");
	}

	@Test
	void cleanPromptWithSafeAnswerHasNoSuggestion() {
		givenBedrockAnswers(true, List.of(), PROMPT);

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isTrue();
		assertThat(result.problems()).isEmpty();
		assertThat(result.improvedVersion()).isEmpty();
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
	void sloppyProblemWithMissingDetailsStillBlocksWithSafeDefaults() {
		Document onlyRuleId = Document.fromMap(Map.of("ruleId", Document.fromString("R-01")));
		givenBedrockAnswers(false, List.of(onlyRuleId), "...");

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.problems()).containsExactly(
				new Problem("R-01", "Customer names", Severity.CRITICAL, BedrockLlmClient.UNSPECIFIED_MESSAGE, ""));
		assertThat(result.isSafe()).isFalse();
	}

	@Test
	void missingIsSafeFlagIsDerivedFromTheProblems() {
		Map<String, Document> withoutFlag = new HashMap<>(toolInput(true, List.of(), PROMPT).asMap());
		withoutFlag.remove("isSafe");
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(toolUse(Document.fromMap(withoutFlag)));

		assertThat(llmClient.check(PROMPT, RULES).isSafe()).isTrue();
	}

	@Test
	void retriesOnceWhenTheModelAnswersWithTextInsteadOfTheTool() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willReturn(response(ContentBlock.fromText("Looks fine to me!"), StopReason.END_TURN))
				.willReturn(toolUse(toolInput(true, List.of(), PROMPT)));

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isTrue();
		verify(bedrock, times(2)).converse(any(ConverseRequest.class));
	}

	@Test
	void anOddlySpacedValueNeverReachesBedrockInParts() {
		String prompt = "Call me, his number is D E 8 9 3704-0044 0532 0130 00 thanks";
		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		given(bedrock.converse(captor.capture())).willAnswer(call -> {
			String sent = call.<ConverseRequest>getArgument(0).messages().getFirst().content().getFirst().text();
			String masked = sent.lines().filter(line -> line.startsWith("Call me")).findFirst().orElseThrow();
			return toolUse(toolInput(true, List.of(), masked));
		});

		CheckResponse result = llmClient.check(prompt, RULES);

		String sent = captor.getValue().messages().getFirst().content().getFirst().text();
		assertThat(sent).doesNotContain("3704").doesNotContain("D E 8 9").contains("Call me, his number is [");
		assertThat(result.problems().getFirst().location()).isEqualTo("D E 8 9 3704-0044 0532 0130 00");
	}

	@Test
	void novaSeesSingleSpacesButThePassageIsMappedBackToTheRealText() {
		String prompt = "Mail to Erika   Musterfrau about the loan.";
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(toolUse(toolInput(false,
				List.of(problem("R-01", "critical", "Name.", "Erika Musterfrau")), "Mail to [X] about the loan.")));

		CheckResponse result = llmClient.check(prompt, RULES);

		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock).converse(captor.capture());
		assertThat(captor.getValue().messages().getFirst().content().getFirst().text())
				.contains("\nMail to Erika Musterfrau about the loan.\n");
		assertThat(result.problems().getFirst().location()).isEqualTo("Erika   Musterfrau");
		assertThat(result.improvedVersion()).isEqualTo("Mail to [CUSTOMER_NAMES_1] about the loan.");
	}

	@Test
	void mapsTheCategoryOfAProblem() {
		givenBedrockAnswers(false, List.of(problem("R-01", "Customer data", "critical", "Contains a name.", "Erika Mustermann")),
				"Please summarise the account of [CUSTOMER_DATA].");

		assertThat(llmClient.check(PROMPT, RULES).problems()).containsExactly(
				new Problem("R-01", "Customer data", Severity.CRITICAL, "Contains a name.", "Erika Mustermann"));
	}

	@Test
	void retriesWithAStricterInstructionWhenASafeAnswerChangedTheInput() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willReturn(toolUse(toolInput(true, List.of(), "Please summarise.")))
				.willReturn(toolUse(toolInput(true, List.of(), PROMPT)));

		CheckResponse result = llmClient.check(PROMPT, RULES);

		assertThat(result.isSafe()).isTrue();
		assertThat(result.improvedVersion()).isEmpty();
		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock, times(2)).converse(captor.capture());
		assertThat(captor.getAllValues().get(0).system().getFirst().text()).doesNotContain(BedrockLlmClient.RETRY_INSTRUCTION);
		assertThat(captor.getAllValues().get(1).system().getFirst().text()).endsWith(BedrockLlmClient.RETRY_INSTRUCTION);
	}

	@Test
	void reportsTheCheckAsIncompleteWhenTheRetryChangesTheInputAgain() {
		givenBedrockAnswers(true, List.of(), "Please summarise.");

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES))
				.isInstanceOfSatisfying(InvalidNovaResponseException.class,
						ex -> assertThat(ex.responseText()).isEqualTo("Please summarise."));
		verify(bedrock, times(2)).converse(any(ConverseRequest.class));
	}

	@Test
	void retriesWhenTheAnswerContainsTheRandomDelimiter() {
		given(bedrock.converse(any(ConverseRequest.class))).willAnswer(call -> {
			List<String> sent = call.<ConverseRequest>getArgument(0).messages().getFirst().content().getFirst().text()
					.lines().toList();
			String marker = sent.get(sent.indexOf("Random string (delimits the user input):") + 1);
			return toolUse(toolInput(false, List.of(problem("R-01", "critical", "Name.", "")), marker + " " + PROMPT));
		});

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES))
				.isInstanceOfSatisfying(LlmException.class,
						ex -> assertThat(ex.code()).isEqualTo(LlmException.INVALID_RESPONSE));
		verify(bedrock, times(2)).converse(any(ConverseRequest.class));
	}

	@Test
	void failsAfterOneRetryWhenTheAnswerStaysUnusable() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willReturn(response(ContentBlock.fromText("Looks fine to me!"), StopReason.END_TURN));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES))
				.isInstanceOfSatisfying(LlmException.class,
						ex -> assertThat(ex.userMessage()).isEqualTo(BedrockLlmClient.MSG_UNUSABLE_ANSWER));
		verify(bedrock, times(2)).converse(any(ConverseRequest.class));
	}

	@Test
	void failsWhenTheProblemsListIsMissing() {
		Map<String, Document> withoutProblems = new HashMap<>(toolInput(true, List.of(), "x").asMap());
		withoutProblems.remove("problems");
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(toolUse(Document.fromMap(withoutProblems)));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	@Test
	void failsWhenOutputWasCutOff() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willReturn(response(ContentBlock.fromText("{\"isSafe\": tr"), StopReason.MAX_TOKENS));

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES)).isInstanceOf(LlmException.class);
	}

	/** Every AWS error gets a fixed, understandable text, is not retried and is never turned into a "safe" result. */
	@ParameterizedTest(name = "{0}")
	@MethodSource("awsErrors")
	void awsErrorsAreExplainedAndNotRetried(String name, SdkException error, String expectedUserMessage) {
		given(bedrock.converse(any(ConverseRequest.class))).willThrow(error);

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES))
				.isInstanceOfSatisfying(LlmException.class, ex -> {
					assertThat(ex.userMessage()).isEqualTo(expectedUserMessage);
					assertThat(ex.getMessage()).isEqualTo("Bedrock call failed: " + error.getClass().getSimpleName());
					assertThat(ex.getCause()).isSameAs(error);
				});
		verify(bedrock, times(1)).converse(any(ConverseRequest.class));
	}

	static Stream<Arguments> awsErrors() {
		return Stream.of(
				Arguments.of("access denied", AccessDeniedException.builder().message("echo: " + PROMPT).build(),
						BedrockLlmClient.MSG_ACCESS_DENIED),
				Arguments.of("throttled", ThrottlingException.builder().message("slow down").build(),
						BedrockLlmClient.MSG_BUSY),
				Arguments.of("validation (wrong model id)", ValidationException.builder().message("echo: " + PROMPT).build(),
						BedrockLlmClient.MSG_MODEL_UNAVAILABLE),
				Arguments.of("model not found", ResourceNotFoundException.builder().message("no model").build(),
						BedrockLlmClient.MSG_MODEL_UNAVAILABLE),
				Arguments.of("no credentials",
						SdkClientException.create("Unable to load credentials from any of the providers in the chain"),
						BedrockLlmClient.MSG_CREDENTIALS),
				Arguments.of("expired SSO session",
						SdkClientException.create("The SSO session associated with this profile has expired"),
						BedrockLlmClient.MSG_CREDENTIALS),
				Arguments.of("invalid security token", BedrockRuntimeException.builder()
								.awsErrorDetails(AwsErrorDetails.builder().errorCode("UnrecognizedClientException").build())
								.message("The security token included in the request is invalid").build(),
						BedrockLlmClient.MSG_CREDENTIALS),
				Arguments.of("network down", SdkClientException.create("Unable to execute HTTP request"),
						BedrockLlmClient.MSG_UNREACHABLE),
				Arguments.of("timeout", ApiCallTimeoutException.create(50_000), BedrockLlmClient.MSG_TIMEOUT));
	}

	@Test
	void userMessagesNeverContainTheRequestContent() {
		given(bedrock.converse(any(ConverseRequest.class)))
				.willThrow(ValidationException.builder().message("echo: " + PROMPT).build());

		assertThatThrownBy(() -> llmClient.check(PROMPT, RULES))
				.isInstanceOfSatisfying(LlmException.class, ex -> {
					assertThat(ex.userMessage()).doesNotContain("Mustermann");
					assertThat(ex.getMessage()).doesNotContain("Mustermann");
				});
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

	private static Document problem(String ruleId, String category, String severity, String message, String location) {
		return Document.fromMap(Map.of(
				"ruleId", Document.fromString(ruleId),
				"category", Document.fromString(category),
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
