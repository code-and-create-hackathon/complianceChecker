package com.compliancechecker.config;

import org.springframework.beans.factory.annotation.Value;
import com.compliancechecker.account.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Requires a login for every /api endpoint ({@link AuthInterceptor}) and lets the browser extension call the API. The website is served by this backend (same origin) and needs no CORS.
 * Allowed origins are configurable via {@code CORS_ALLOWED_ORIGINS} (comma-separated patterns).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOriginPatterns;
	private final AuthInterceptor authInterceptor;

	public WebConfig(@Value("${cors.allowed-origin-patterns}") String[] allowedOriginPatterns,
			AuthInterceptor authInterceptor) {
		this.allowedOriginPatterns = allowedOriginPatterns;
		this.authInterceptor = authInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns(allowedOriginPatterns)
				.allowedMethods("GET", "POST", "PUT")
				.allowedHeaders("Content-Type", "Authorization")
				.allowCredentials(false);
	}
}
