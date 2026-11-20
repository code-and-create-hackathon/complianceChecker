package com.compliancechecker.llm;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.ProblemCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulated compliance check with a few simple pattern rules, for local development and demos without AWS.
 *
 * <p>This is not a real compliance check: it ignores the uploaded compliance rules and only finds the patterns
 * below. Every result is marked with {@code simulated = true}.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

	private static final List<Rule> RULES = List.of(
			new Rule("Contains an email address.", ProblemCategory.CUSTOMER_DATA, "[EMAIL]",
					Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
			new Rule("Contains a credit card number.", ProblemCategory.ACCOUNT_DATA, "[CARD_NUMBER]",
					Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)"), MockLlmClient::passesLuhnCheck),
			new Rule("Contains an IBAN.", ProblemCategory.ACCOUNT_DATA, "[IBAN]",
					Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?\\b")),
			new Rule("Contains a phone number.", ProblemCategory.CUSTOMER_DATA, "[PHONE]",
					// Starts with +, 00 or 0, so ordinary numbers like amounts or years are not matched.
					Pattern.compile("(?<![\\w+])(?:(?:\\+|00)[1-9]\\d{0,2}|0)[ /-]?\\(?\\d{2,5}\\)?(?:[ /-]?\\d{2,}){1,4}(?!\\w)")),
			new Rule("Contains confidential company information.", ProblemCategory.INSIDER_INFO,
					"[CONFIDENTIAL CONTENT REMOVED]",
					// The whole sentence around a keyword is removed.
					Pattern.compile("(?:[^.!?\\s][^.!?\\n]*)?\\b(?:confidential|internal only|not public|secret|insider|"
							+ "vertraulich|nur intern|nicht öffentlich|geheim|unveröffentlicht)\\b[^.!?\\n]*[.!?]?",
							Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)));

	public MockLlmClient() {
		log.warn("LLM provider 'mock' is active: results are simulated, no AWS calls are made.");
	}

	@Override
	public CheckResponse check(String prompt, String complianceRules) {
		String improvedVersion = prompt;
		List<String> problems = new ArrayList<>();
		List<ProblemCategory> categories = new ArrayList<>();

		for (Rule rule : RULES) {
			String replaced = rule.replaceMatches(improvedVersion);
			if (!replaced.equals(improvedVersion)) {
				improvedVersion = replaced;
				problems.add(rule.problem());
				if (!categories.contains(rule.category())) {
					categories.add(rule.category());
				}
			}
		}
		return new CheckResponse(problems.isEmpty(), problems, categories, improvedVersion, true);
	}

	@Override
	public String modelId() {
		return "mock";
	}

	/** Luhn checksum used by all payment card numbers; filters out random digit sequences. */
	static boolean passesLuhnCheck(String candidate) {
		String digits = candidate.replaceAll("\\D", "");
		int sum = 0;
		for (int i = 0; i < digits.length(); i++) {
			int digit = digits.charAt(digits.length() - 1 - i) - '0';
			if (i % 2 == 1) {
				digit = digit * 2 > 9 ? digit * 2 - 9 : digit * 2;
			}
			sum += digit;
		}
		return sum % 10 == 0;
	}

	/**
	 * A pattern for one kind of sensitive data, the placeholder that replaces it, and an optional extra check a
	 * match must pass.
	 */
	private record Rule(String problem, ProblemCategory category, String placeholder, Pattern pattern,
			Predicate<String> isMatch) {

		Rule(String problem, ProblemCategory category, String placeholder, Pattern pattern) {
			this(problem, category, placeholder, pattern, match -> true);
		}

		String replaceMatches(String text) {
			Matcher matcher = pattern.matcher(text);
			StringBuilder result = new StringBuilder();
			while (matcher.find()) {
				String replacement = isMatch.test(matcher.group()) ? placeholder : matcher.group();
				matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
			}
			matcher.appendTail(result);
			return result.toString();
		}
	}
}
