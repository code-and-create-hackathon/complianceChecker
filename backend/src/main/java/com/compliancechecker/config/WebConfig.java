package com.compliancechecker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the browser extension call the API. The website is served by this backend (same origin) and needs no CORS.
 * Allowed origins are configurable via {@code CORS_ALLOWED_ORIGINS} (comma-separated patterns).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOriginPatterns;

	public WebConfig(@Value("${cors.allowed-origin-patterns}") String[] allowedOriginPatterns) {
		this.allowedOriginPatterns = allowedOriginPatterns;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns(allowedOriginPatterns)
				.allowedMethods("GET", "POST")
				.allowedHeaders("Content-Type")
				.allowCredentials(false);
	}
}
