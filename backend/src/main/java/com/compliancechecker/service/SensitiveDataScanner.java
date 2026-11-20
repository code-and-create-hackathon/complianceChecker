package com.compliancechecker.service;

import com.compliancechecker.model.ProblemCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a few kinds of sensitive data with simple pattern rules and replaces them with placeholders.
 *
 * <p>Used by the mock LLM and as a last safety net before anything is written to the history. It only knows the
 * patterns below and is not a complete compliance check.
 */
@Component
public class SensitiveDataScanner {

	private static final List<Rule> RULES = List.of(
			new Rule("Contains an email address.", ProblemCategory.CUSTOMER_DATA, "[EMAIL]",
					Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
			new Rule("Contains a credit card number.", ProblemCategory.ACCOUNT_DATA, "[CARD_NUMBER]",
					Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)"), SensitiveDataScanner::passesLuhnCheck),
			new Rule("Contains an IBAN.", ProblemCategory.ACCOUNT_DATA, "[IBAN]",
					Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?\\b")),
			new Rule("Contains a phone number.", ProblemCategory.CUSTOMER_DATA, "[PHONE]",
					// Starts with +, 00 or 0, so ordinary numbers like amounts or years are not matched.
					Pattern.compile("(?<![\\w+])(?:(?:\\+|00)[1-9]\\d{0,2}|0)[ /-]?\\(?\\d{2,5}\\)?(?:[ /-]?\\d{2,}){1,4}(?!\\w)")),
			new Rule("Contains confidential company information.", ProblemCategory.INSIDER_INFO,
					"[SENSITIVE SENTENCE REMOVED]",
					// The whole sentence around a keyword is removed.
					Pattern.compile("(?:[^.!?\\s][^.!?\\n]*)?\\b(?:confidential|internal only|not public|secret|insider|"
							+ "vertraulich|nur intern|nicht öffentlich|geheim|unveröffentlicht)\\b[^.!?\\n]*[.!?]?",
							Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)));

	/**
	 * @param redactedText the text with every finding replaced by a placeholder
	 * @param problems     one entry per kind of finding
	 * @param categories   categories of the findings, without duplicates
	 */
	public record ScanResult(String redactedText, List<String> problems, List<ProblemCategory> categories) {

		public boolean foundSensitiveData() {
			return !problems.isEmpty();
		}
	}

	public ScanResult scan(String text) {
		String redactedText = text;
		List<String> problems = new ArrayList<>();
		List<ProblemCategory> categories = new ArrayList<>();

		for (Rule rule : RULES) {
			String replaced = rule.replaceMatches(redactedText);
			if (!replaced.equals(redactedText)) {
				redactedText = replaced;
				problems.add(rule.problem());
				if (!categories.contains(rule.category())) {
					categories.add(rule.category());
				}
			}
		}
		return new ScanResult(redactedText, List.copyOf(problems), List.copyOf(categories));
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
