package com.compliancechecker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.time.Duration;

/**
 * Creates the Bedrock client. Credentials come from the default AWS provider chain (environment, profile/SSO, IAM
 * role) and are resolved on the first call. Only active with {@code llm.provider=bedrock}.
 */
@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "bedrock")
public class BedrockConfig {

	@Bean
	BedrockRuntimeClient bedrockRuntimeClient(@Value("${llm.aws-region}") String awsRegion) {
		return BedrockRuntimeClient.builder()
				.region(Region.of(awsRegion))
				// Below the web client's 60 s limit (API.md), so the user gets a clean 502 instead of a client timeout.
				.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(50)))
				.build();
	}
}
