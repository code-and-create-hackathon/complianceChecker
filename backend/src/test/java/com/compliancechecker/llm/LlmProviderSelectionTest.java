package com.compliancechecker.llm;

import com.compliancechecker.config.BedrockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies which LlmClient is active. Creating the Bedrock client does not call AWS. */
class LlmProviderSelectionTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(MockLlmClient.class, BedrockLlmClient.class, BedrockConfig.class)
			.withPropertyValues("llm.model-id=test-model", "llm.aws-region=eu-north-1");

	@Test
	void usesMockByDefaultAndCreatesNoAwsClient() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(LlmClient.class);
			assertThat(context.getBean(LlmClient.class)).isInstanceOf(MockLlmClient.class);
			assertThat(context).doesNotHaveBean(BedrockRuntimeClient.class);
		});
	}

	@Test
	void usesBedrockOnlyWhenConfigured() {
		contextRunner.withPropertyValues("llm.provider=bedrock").run(context -> {
			assertThat(context).hasSingleBean(LlmClient.class);
			assertThat(context.getBean(LlmClient.class)).isInstanceOf(BedrockLlmClient.class);
		});
	}
}
