package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Finding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AutoToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asks the LLM (Amazon Bedrock, Converse API) whether a prompt complies with the given rules.
 *
 * <p>The model reports its result by calling the {@value #TOOL_NAME} tool because Nova does not return this
 * application-specific response schema natively.
 * structured output. Any answer that is not a complete, valid tool call raises an {@link LlmException}, so a failed
 * check never approves a prompt.
 *
 * <p>Used with {@code llm.provider=bedrock} and by the Nova console client.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "bedrock")
public class BedrockLlmClient implements LlmClient {
	private static final Logger log = LoggerFactory.getLogger(BedrockLlmClient.class);

	static final String TOOL_NAME = "report_compliance_result";

	private static final int MAX_OUTPUT_TOKENS = 4096;

	private static final String SYSTEM_PROMPT = loadSystemPrompt();

	private final BedrockRuntimeClient bedrock;
	private final String modelId;

	public BedrockLlmClient(BedrockRuntimeClient bedrock, @Value("${llm.model-id}") String modelId) {
		this.bedrock = bedrock;
		this.modelId = modelId;
	}

	@Override
	public CheckResponse check(String prompt, String complianceRules) {
		ConverseResponse response;
		try {
			response = bedrock.converse(buildRequest(prompt, complianceRules));
		}
		catch (SdkException ex) {
			// Only the exception type: AWS error messages may echo request details.
			int status = ex instanceof AwsServiceException aws ? aws.statusCode() : 0;
			log.warn("Nova request failed exception={} status={}", ex.getClass().getSimpleName(), status);
			throw new LlmException("Bedrock call failed: " + ex.getClass().getSimpleName(), ex);
		}
		try {
			CheckResponse result = toCheckResponse(findToolInput(response));
			if (result.isSafe() && !prompt.equals(result.improvedVersion())) {
				throw new LlmException("A conforming result must preserve the complete user input");
			}
			if (!result.isSafe() && result.improvedVersion().isBlank()) {
				throw new LlmException("A non-conforming result must include a censored user input");
			}
			return result;
		} catch (LlmException ex) {
			log.warn("Nova response rejected reason={} stopReason={}", ex.getMessage(), response.stopReason());
			throw ex;
		}
	}

	@Override
	public String modelId() {
		return modelId;
	}

	private ConverseRequest buildRequest(String prompt, String complianceRules) {
		Message userMessage = Message.builder()
				.role(ConversationRole.USER)
				.content(ContentBlock.fromText(buildUserMessage(prompt, complianceRules)))
				.build();

		return ConverseRequest.builder()
				.modelId(modelId)
				.system(SystemContentBlock.fromText(SYSTEM_PROMPT))
				.messages(userMessage)
				.toolConfig(ToolConfiguration.builder()
						.tools(Tool.fromToolSpec(resultToolSpec()))
						// "auto" is documented for all tool-use models; forcing a tool is not documented for Nova 2.
						.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build()))
						.build())
				.inferenceConfig(InferenceConfiguration.builder()
						.maxTokens(MAX_OUTPUT_TOKENS)
						.temperature(0f)
						.build())
				.build();
	}

	private static String buildUserMessage(String prompt, String complianceRules) {
		String marker = UUID.randomUUID().toString();
		return """
				Compliance-Richtlinien:
				%s
				Random String (begrenzt den User-Input):
				%s
				%s
				%s
				""".formatted(complianceRules, marker, prompt, marker);
	}

	private static String loadSystemPrompt() {
		try (var input = new ClassPathResource("prompts/compliance-hardprompt.md").getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException("Compliance hardprompt resource could not be loaded.", ex);
		}
	}

	private static Document findToolInput(ConverseResponse response) {
		if (response.output() == null || response.output().message() == null) {
			throw new LlmException("Bedrock returned no message");
		}
		return response.output().message().content().stream()
				.map(ContentBlock::toolUse)
				.filter(toolUse -> toolUse != null && TOOL_NAME.equals(toolUse.name()))
				.map(ToolUseBlock::input)
				.findFirst()
				.orElseThrow(() -> new LlmException("Model did not report a result, stopReason=" + response.stopReason()));
	}

	private static CheckResponse toCheckResponse(Document input) {
		if (input == null || !input.isMap()) {
			throw new LlmException("Result is not an object");
		}
		Map<String, Document> fields = input.asMap();
		boolean isSafe = requireBoolean(fields, "isSafe");
		List<Finding> findings = requireList(fields, "problems").stream().map(item -> {
			if (!item.isMap()) throw new LlmException("Invalid finding");
			var data = item.asMap();
			String ruleId = requireString(data.get("ruleId"));
			String category = requireString(data.get("category"));
			if (ruleId.isBlank() || category.isBlank()) {
				throw new LlmException("Invalid finding: ruleId or category is empty");
			}
			return new Finding(ruleId, category);
		}).toList();
		String improvedVersion = requireString(fields.get("improvedVersion"));
		boolean computedSafe = findings.isEmpty();
		if (isSafe != computedSafe) throw new LlmException("Inconsistent compliance result");
		return new CheckResponse(isSafe, findings.stream().map(Finding::category).toList(), List.of(),
				improvedVersion, false, findings);
	}

	private static boolean requireBoolean(Map<String, Document> fields, String name) {
		Document value = fields.get(name);
		if (value == null || !value.isBoolean()) {
			throw new LlmException("Result field is missing or invalid: " + name);
		}
		return value.asBoolean();
	}

	private static List<Document> requireList(Map<String, Document> fields, String name) {
		Document value = fields.get(name);
		if (value == null || !value.isList()) {
			throw new LlmException("Result field is missing or invalid: " + name);
		}
		return value.asList();
	}

	private static String requireString(Document value) {
		if (value == null || !value.isString()) {
			throw new LlmException("Result contains a missing or non-text value");
		}
		return value.asString();
	}

	private static ToolSpecification resultToolSpec() {
		Document string = Document.fromMap(Map.of("type", Document.fromString("string")));
		Document finding = Document.fromMap(Map.of(
				"type", Document.fromString("object"),
				"properties", Document.fromMap(Map.of("ruleId", string, "category", string)),
				"required", Document.fromList(List.of("ruleId", "category").stream().map(Document::fromString).toList())));

		Document schema = Document.fromMap(Map.of(
				"type", Document.fromString("object"),
				"properties", Document.fromMap(Map.of(
						"isSafe", Document.fromMap(Map.of("type", Document.fromString("boolean"))),
						"problems", arrayOf(finding),
						"improvedVersion", Document.fromMap(Map.of("type", Document.fromString("string"))))),
				"required", Document.fromList(List.of(
						Document.fromString("isSafe"),
						Document.fromString("problems"),
						Document.fromString("improvedVersion")))));

		return ToolSpecification.builder()
				.name(TOOL_NAME)
				.description("Reports the result of the compliance check.")
				.inputSchema(ToolInputSchema.fromJson(schema))
				.build();
	}

	private static Document arrayOf(Document itemSchema) {
		return Document.fromMap(Map.of("type", Document.fromString("array"), "items", itemSchema));
	}
}
