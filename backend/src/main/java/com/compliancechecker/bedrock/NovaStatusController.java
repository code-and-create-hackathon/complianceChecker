package com.compliancechecker.bedrock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

@RestController
@ConditionalOnProperty(prefix = "checker.bedrock-console", name = "enabled", havingValue = "true")
class NovaStatusController {

	private final BedrockNovaClient nova;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(4))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	NovaStatusController(BedrockNovaClient nova) {
		this.nova = nova;
	}

	@GetMapping("/api/nova/status")
	ResponseEntity<Status> status() {
		var connection = nova.currentConnection();
		if (connection == null) {
			return response(new Status("not_configured", "unverified", null));
		}

		String endpoint = "https://bedrock-runtime." + connection.region() + ".amazonaws.com/";
		var request = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(Duration.ofSeconds(5))
				.method("HEAD", HttpRequest.BodyPublishers.noBody())
				.build();
		try {
			http.send(request, HttpResponse.BodyHandlers.discarding());
			return response(new Status("reachable", nova.lastOutcome(), nova.lastLatencyMs()));
		} catch (IOException ex) {
			return response(new Status("unreachable", nova.lastOutcome(), nova.lastLatencyMs()));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return response(new Status("unreachable", nova.lastOutcome(), nova.lastLatencyMs()));
		}
	}

	private ResponseEntity<Status> response(Status status) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status);
	}

	record Status(String connection, String lastOutcome, Long lastLatencyMs) { }
}
