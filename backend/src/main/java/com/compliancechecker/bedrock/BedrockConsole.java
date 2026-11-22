package com.compliancechecker.bedrock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.awt.Desktop;
import java.net.URI;
import org.springframework.core.env.Environment;

@Component
@ConditionalOnProperty(prefix = "checker.bedrock-console", name = "enabled", havingValue = "true")
class BedrockConsole {

	private final BedrockNovaClient nova;
	private final Environment environment;

	BedrockConsole(BedrockNovaClient nova, Environment environment) {
		this.nova = nova;
		this.environment = environment;
	}

	@EventListener(ApplicationReadyEvent.class)
	void openConsole() {
		Thread.ofPlatform().name("bedrock-console").daemon().start(this::readPrompts);
	}

	private void readPrompts() {
		Console console = System.console();
		var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String suppliedToken = System.getenv("CHECKER_NOVA_CONSOLE_TOKEN");
		BedrockNovaClient.Connection connection = null;
		try {
			connection = suppliedToken == null || suppliedToken.isBlank()
					? readConnection(console) : connect(suppliedToken.strip(), null, null);
			if (connection == null) {
				return;
			}
			nova.activate(connection);
			openInterface();
			System.out.printf("Nova console ready (%s). /exit closes the input, /token changes the credentials.%n",
					connection.region());
			String prompt;
			while ((prompt = readLine(console, input)) != null) {
				String command = prompt.strip();
				if (command.isEmpty()) {
					continue;
				}
				if (command.equals("/exit")) {
					return;
				}
				if (command.equals("/token")) {
					var replacement = readConnection(console);
					if (replacement != null) {
						connection = replacement;
						nova.activate(connection);
						System.out.printf("Credentials updated (%s).%n", connection.region());
					}
					continue;
				}
				sendAndPrint(prompt, connection);
			}
		} catch (IOException ex) {
			System.err.println("Warning: terminal input is not available.");
		}
	}

	private void openInterface() {
		String port = environment.getProperty("local.server.port", "8080");
		String url = "http://localhost:" + port + "/";
		System.out.println("Frontend: " + url);
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
			}
		} catch (IOException | UnsupportedOperationException | SecurityException ex) {
			System.err.println("Please open the frontend in the browser: " + url);
		}
	}

	private static String readLine(Console console, BufferedReader input) throws IOException {
		if (console != null) {
			return console.readLine("Nova> ");
		}
		System.out.print("Nova> ");
		System.out.flush();
		return input.readLine();
	}

	private BedrockNovaClient.Connection readConnection(Console console) {
		if (console == null) {
			System.err.println("Warning: use an interactive terminal to enter the credentials.");
			return null;
		}
		String key = readSecret(console, "AWS access key ID or Bedrock API key: ");
		if (key.isEmpty()) {
			System.err.println("Warning: enter an AWS or Bedrock key.");
			return null;
		}
		boolean temporary = key.startsWith("ASIA");
		if (!temporary && !key.startsWith("AKIA")) {
			return connect(key, null, null);
		}
		String secret = readSecret(console, "AWS secret access key: ");
		if (secret.isEmpty()) {
			System.err.println("Warning: the AWS secret access key is missing.");
			return null;
		}
		String sessionToken = temporary ? readSecret(console, "AWS session token: ") : null;
		if (temporary && sessionToken.isEmpty()) {
			System.err.println("Warning: temporary AWS keys need a session token.");
			return null;
		}
		return connect(key, secret, sessionToken);
	}

	private BedrockNovaClient.Connection connect(String key, String secret, String sessionToken) {
		try {
			return nova.connect(key, secret, sessionToken);
		} catch (BedrockNovaClient.UnsupportedNovaRegionException ex) {
			System.err.println("Warning: " + ex.getMessage());
		} catch (SdkException | IllegalArgumentException ex) {
			System.err.println("Warning: the Bedrock connection could not be set up.");
		}
		return null;
	}

	private static String readSecret(Console console, String prompt) {
		char[] input = console.readPassword("%s", prompt);
		if (input == null) {
			return "";
		}
		try {
			return new String(input).strip();
		} finally {
			Arrays.fill(input, '\0');
		}
	}

	private void sendAndPrint(String prompt, BedrockNovaClient.Connection connection) {
		try {
			System.out.println(nova.sendPrompt(prompt));
		} catch (BedrockRuntimeException ex) {
			int status = ex.statusCode();
			if (status == 401 || status == 403) {
				System.err.printf("Warning: AWS denies Bedrock access in %s (HTTP %d). "
						+ "Check the credentials and bedrock:InvokeModel. Change them with /token.%n", connection.region(), status);
			} else {
				System.err.printf("Warning: Bedrock answered HTTP %d. The input stays open.%n", status);
			}
		} catch (IOException | SdkException | IllegalArgumentException ex) {
			System.err.println("Warning: Bedrock is not reachable or the answer is unusable. "
					+ "The input stays open.");
		}
	}
}
