package com.compliancechecker.bedrock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.token.credentials.StaticTokenProvider;
import software.amazon.awssdk.http.auth.aws.scheme.AwsV4AuthScheme;
import software.amazon.awssdk.http.auth.scheme.BearerAuthScheme;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.auth.scheme.BedrockRuntimeAuthSchemeProvider;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;
import com.compliancechecker.llm.BedrockLlmClient;
import com.compliancechecker.llm.LlmException;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.history.SubmittedPrompt;
import com.compliancechecker.history.SubmittedPromptRepository;

@Component
@ConditionalOnProperty(prefix = "checker.bedrock-console", name = "enabled", havingValue = "true")
class BedrockNovaClient {

	private static final String DEFAULT_REGION = "eu-central-1";
	private static final String NOVA_MICRO = "amazon.nova-micro-v1:0";
	private static final String REGION_PATTERN = "[a-z]{2}(?:-[a-z]+)+-[0-9]+";
	private static final String API_KEY_PREFIX = "bedrock-api-key-";
	private static final Pattern TOKEN_CREDENTIAL = Pattern.compile("[?&]X-Amz-Credential=([^&]+)");
	private static final Pattern TOKEN_SCOPE = Pattern.compile("[^/]+/[0-9]{8}/(" + REGION_PATTERN + ")/bedrock/aws4_request");

	private final String configuredRegion;
	private final String configuredModelId;
	private Connection activeConnection;
	private final SubmittedPromptRepository prompts;

	BedrockNovaClient(@Value("${checker.bedrock-console.region:}") String region,
			@Value("${checker.bedrock-console.model-id:}") String modelId, SubmittedPromptRepository prompts) {
		if ((!region.isEmpty() && !region.matches(REGION_PATTERN))
				|| (!modelId.isEmpty() && !modelId.matches("[a-zA-Z0-9._:-]+"))) {
			throw new IllegalArgumentException("Ungültige Bedrock-Region oder Modell-ID.");
		}
		this.configuredRegion = region;
		this.configuredModelId = modelId;
		this.prompts = prompts;
	}

	Connection connect(String key, String secret, String sessionToken) {
		boolean useIam = secret != null;
		String region = configuredRegion.isEmpty() ? (useIam ? DEFAULT_REGION : tokenRegion(key)) : configuredRegion;
		String modelId = configuredModelId.isEmpty() ? defaultModelId(region) : configuredModelId;
		String schemeId = useIam ? AwsV4AuthScheme.SCHEME_ID : BearerAuthScheme.SCHEME_ID;
		var defaultSchemes = BedrockRuntimeAuthSchemeProvider.defaultProvider();
		var builder = BedrockRuntimeClient.builder()
				.region(Region.of(region))
				.endpointOverride(URI.create("https://bedrock-runtime." + region + ".amazonaws.com"))
				.authSchemeProvider(params -> defaultSchemes.resolveAuthScheme(params).stream()
						.filter(option -> schemeId.equals(option.schemeId()))
						.toList())
				.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(60)));
		if (useIam) {
			var credentials = sessionToken == null ? AwsBasicCredentials.create(key, secret)
					: AwsSessionCredentials.create(key, secret, sessionToken);
			builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
		} else {
			builder.tokenProvider(StaticTokenProvider.create(() -> key));
		}
		return new Connection(builder.build(), region, modelId);
	}

	synchronized void activate(Connection connection) {
		Connection previous = activeConnection;
		activeConnection = connection;
		if (previous != null) previous.close();
	}

	synchronized CheckResponse check(String prompt, String rules) {
		if (activeConnection == null) throw new LlmException("Nova credentials have not been entered");
		prompts.save(new SubmittedPrompt(prompt));
		return new BedrockLlmClient(activeConnection.client(), activeConnection.modelId()).check(prompt, rules);
	}

	synchronized String sendPrompt(String prompt) throws IOException {
		if (activeConnection == null) throw new LlmException("Nova credentials have not been entered");
		prompts.save(new SubmittedPrompt(prompt));
		return activeConnection.sendPrompt(prompt);
	}

	@PreDestroy
	synchronized void close() {
		if (activeConnection != null) activeConnection.close();
	}

	private static String tokenRegion(String key) {
		if (!key.startsWith(API_KEY_PREFIX)) {
			return DEFAULT_REGION;
		}
		// AWS short-term keys contain a Base64-encoded SigV4 query. Use only the region from its credential scope.
		try {
			var decoded = Base64.getDecoder().decode(key.substring(API_KEY_PREFIX.length()));
			var credential = TOKEN_CREDENTIAL.matcher(new String(decoded, StandardCharsets.UTF_8));
			if (credential.find()) {
				var scope = TOKEN_SCOPE.matcher(URLDecoder.decode(credential.group(1), StandardCharsets.UTF_8));
				if (scope.matches()) {
					return scope.group(1);
				}
			}
		} catch (IllegalArgumentException ex) {
			// Opaque keys also use the default endpoint.
		}
		return DEFAULT_REGION;
	}

	private static String defaultModelId(String region) {
		return switch (region) {
			case "eu-west-2", "us-east-1", "us-gov-west-1", "ap-southeast-2" -> NOVA_MICRO;
			case "eu-central-1", "eu-north-1", "eu-south-1", "eu-south-2", "eu-west-1", "eu-west-3", "il-central-1"
					-> "eu." + NOVA_MICRO;
			case "us-east-2", "us-west-2" -> "us." + NOVA_MICRO;
			case "ap-northeast-1", "ap-northeast-2", "ap-south-1", "ap-southeast-1" -> "apac." + NOVA_MICRO;
			default -> throw new UnsupportedNovaRegionException(region);
		};
	}

	static final class UnsupportedNovaRegionException extends IllegalArgumentException {

		UnsupportedNovaRegionException(String region) {
			super("Nova Micro ist in " + region + " nicht verfügbar.");
		}
	}

	record Connection(BedrockRuntimeClient client, String region, String modelId) implements AutoCloseable {

		String sendPrompt(String prompt) throws IOException {
			var response = client.converse(request -> request.modelId(modelId)
					.messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(prompt)).build())
					.inferenceConfig(config -> config.maxTokens(512)));
			var message = response.output() == null ? null : response.output().message();
			if (message == null) {
				throw new IOException("Bedrock hat keinen Antworttext geliefert.");
			}
			String answer = message.content().stream()
					.map(ContentBlock::text)
					.filter(Objects::nonNull)
					.collect(Collectors.joining());
			if (answer.isEmpty()) {
				throw new IOException("Bedrock hat keinen Antworttext geliefert.");
			}
			return answer;
		}

		@Override
		public void close() {
			client.close();
		}
	}
}
