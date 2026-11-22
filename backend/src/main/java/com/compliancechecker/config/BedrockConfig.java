package com.compliancechecker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.time.Duration;

/**
 * Creates the Bedrock client. Credentials come from the default AWS provider chain (environment, profile/SSO, IAM
 * role) and are resolved on the first call. Active unless {@code llm.provider=mock}.
 */
@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "bedrock", matchIfMissing = true)
public class BedrockConfig {

	private static final Logger log = LoggerFactory.getLogger(BedrockConfig.class);

	@Bean
	BedrockRuntimeClient bedrockRuntimeClient(@Value("${llm.aws-region}") String awsRegion,
			@Value("${llm.model-id}") String modelId) {
		log.warn("LLM provider 'bedrock' is active: model={} region={}. Every check is a paid AWS call.", modelId,
				awsRegion);
		return BedrockRuntimeClient.builder()
				.region(Region.of(awsRegion))
				// Below the web client's 60 s limit (API.md), so the user gets a clean 502 instead of a client timeout.
				.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(50)))
				.build();
	}
}
