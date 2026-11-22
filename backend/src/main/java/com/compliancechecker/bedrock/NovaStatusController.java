package com.compliancechecker.bedrock;

import com.compliancechecker.account.PublicEndpoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Live status for the badge on the website: is the Bedrock endpoint reachable, and how did the last check go. Works
 * with the default provider (AWS profile) and in Nova console mode (credentials typed in at startup).
 */
@RestController
class NovaStatusController {

	private final NovaStatus status;
	private final ObjectProvider<BedrockNovaClient> console;
	private final String provider;
	private final String region;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(4))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	NovaStatusController(NovaStatus status, ObjectProvider<BedrockNovaClient> console,
			@Value("${llm.provider:bedrock}") String provider, @Value("${llm.aws-region:eu-north-1}") String region) {
		this.status = status;
		this.console = console;
		this.provider = provider;
		this.region = region;
	}

	/** Public: shows only whether Nova is reachable, also on the login page. */
	@PublicEndpoint
	@GetMapping("/api/nova/status")
	ResponseEntity<Status> status() {
		if ("mock".equals(provider)) {
			return response(new Status("mock", status.lastOutcome(), status.lastLatencyMs()));
		}
		String activeRegion = region;
		BedrockNovaClient nova = console.getIfAvailable();
		if (nova != null) {
			var connection = nova.currentConnection();
			if (connection == null) {
				return response(new Status("not_configured", "unverified", null));
			}
			activeRegion = connection.region();
		}
		String endpoint = "https://bedrock-runtime." + activeRegion + ".amazonaws.com/";
		var request = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(Duration.ofSeconds(5))
				.method("HEAD", HttpRequest.BodyPublishers.noBody())
				.build();
		try {
			http.send(request, HttpResponse.BodyHandlers.discarding());
			return response(new Status("reachable", status.lastOutcome(), status.lastLatencyMs()));
		}
		catch (IOException ex) {
			return response(new Status("unreachable", status.lastOutcome(), status.lastLatencyMs()));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return response(new Status("unreachable", status.lastOutcome(), status.lastLatencyMs()));
		}
	}

	private ResponseEntity<Status> response(Status body) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
	}

	record Status(String connection, String lastOutcome, Long lastLatencyMs) { }
}
