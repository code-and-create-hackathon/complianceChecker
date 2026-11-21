package com.compliancechecker.llm;

import com.compliancechecker.model.Finding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class BedrockLlmClientTest {
	private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
	private final BedrockLlmClient client = new BedrockLlmClient(bedrock, "nova-test");

	@Test
	void mapsFindingsAndSendsPromptAndRules() {
		answer(false, List.of(finding("R-02", "critical", "IBAN found", "DE123")), "[IBAN_1]");
		var result = client.check("Pay DE123", "R-02: no IBANs");
		assertThat(result.findings()).containsExactly(new Finding("R-02", "critical", "IBAN found", "DE123"));
		assertThat(result.isSafe()).isFalse();
		var request = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(bedrock).converse(request.capture());
		assertThat(request.getValue().system().getFirst().text()).contains("R-02: no IBANs");
		assertThat(request.getValue().messages().getFirst().content().getFirst().text()).contains("Pay DE123");
	}

	@Test
	void lowSeverityAllowsSending() {
		answer(true, List.of(finding("R-01", "low", "Notice", "")), "");
		assertThat(client.check("hello", "rules").isSafe()).isTrue();
	}

	@Test
	void inconsistentOrMalformedFindingsFailClosed() {
		answer(true, List.of(finding("R-01", "high", "Violation", "")), "");
		assertThatThrownBy(() -> client.check("hello", "rules")).isInstanceOf(LlmException.class);
		answer(false, List.of(finding("", "bad", "Violation", "")), "");
		assertThatThrownBy(() -> client.check("hello", "rules")).isInstanceOf(LlmException.class);
	}

	@Test
	void missingToolAndAwsFailureFailClosed() {
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(ConverseResponse.builder().build());
		assertThatThrownBy(() -> client.check("hello", "rules")).isInstanceOf(LlmException.class);
		given(bedrock.converse(any(ConverseRequest.class))).willThrow(SdkClientException.create("private"));
		assertThatThrownBy(() -> client.check("hello", "rules")).isInstanceOf(LlmException.class)
				.hasMessage("Bedrock call failed: SdkClientException");
	}

	private void answer(boolean safe, List<Document> problems, String improved) {
		var input = Document.fromMap(Map.of("isSafe", Document.fromBoolean(safe),
				"problems", Document.fromList(problems), "improvedVersion", Document.fromString(improved)));
		var tool = ToolUseBlock.builder().toolUseId("t").name(BedrockLlmClient.TOOL_NAME).input(input).build();
		var message = Message.builder().role(ConversationRole.ASSISTANT).content(ContentBlock.fromToolUse(tool)).build();
		given(bedrock.converse(any(ConverseRequest.class))).willReturn(ConverseResponse.builder()
				.output(ConverseOutput.fromMessage(message)).stopReason(StopReason.TOOL_USE).build());
	}

	private static Document finding(String id, String severity, String message, String location) {
		return Document.fromMap(Map.of("ruleId", Document.fromString(id), "severity", Document.fromString(severity),
				"message", Document.fromString(message), "location", Document.fromString(location)));
	}
}
