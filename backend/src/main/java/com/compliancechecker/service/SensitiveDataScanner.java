package com.compliancechecker.service;

import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a few kinds of sensitive data with simple pattern rules and replaces them with numbered placeholders.
 *
 * <p>Used by the mock LLM and as a last safety net before anything is written to the history. It only knows the
 * patterns below and is not a complete compliance check. Rule ids and severities follow
 * {@code Testdata/compliance-guide-musterfinanz.md}, like the frontend's demo mode.
 */
@Component
public class SensitiveDataScanner {

	/** Order matters: when matches overlap, the rule listed first wins. */
	private static final List<Rule> RULES = List.of(
			// First, so a confidential sentence is removed as a whole, including anything inside it.
			new Rule("R-09", Severity.MEDIUM, "Contains information marked as confidential.", "REMOVED",
					confidentialSentencePattern()),
			new Rule("R-02", Severity.CRITICAL, "Contains an IBAN. Bank details must not be sent to AI systems.", "IBAN",
					Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?\\b")),
			new Rule("R-02", Severity.CRITICAL, "Contains a credit card number.", "CARD_NUMBER",
					Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)"), SensitiveDataScanner::passesLuhnCheck),
			new Rule("R-01", Severity.CRITICAL, "Contains an email address.", "EMAIL",
					Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
			new Rule("R-01", Severity.CRITICAL, "Contains a phone number.", "PHONE",
					// Starts with +, 00 or 0, so ordinary numbers like amounts or years are not matched.
					Pattern.compile("(?<![\\w+])(?:(?:\\+|00)[1-9]\\d{0,2}|0)[ /-]?\\(?\\d{2,5}\\)?(?:[ /-]?\\d{2,}){1,4}(?!\\w)")));

	/**
	 * The whole sentence around a confidentiality keyword. A ".", "!" or "?" only ends a sentence if whitespace or
	 * the end follows, so e-mail addresses and decimals do not split it.
	 */
	private static Pattern confidentialSentencePattern() {
		String sentenceChar = "(?:[^.!?\\n]|[.!?](?=\\S))";
		String keywords = "confidential|internal only|not public|insider\\w*|vertraulich\\w*|nur intern|nicht öffentlich";
		return Pattern.compile("(?:(?=\\S)" + sentenceChar + "*?)?\\b(?:" + keywords + ")\\b" + sentenceChar + "*[.!?]?",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
	}

	/**
	 * @param problems     one problem per match, in the order of their position in the text
	 * @param redactedText the text with every match replaced by a numbered placeholder like {@code [EMAIL_1]}
	 */
	public record ScanResult(List<Problem> problems, String redactedText) {

		public boolean foundSensitiveData() {
			return !problems.isEmpty();
		}
	}

	public ScanResult scan(String text) {
		List<Match> matches = findMatches(text);
		List<Problem> problems = matches.stream()
				.map(match -> new Problem(match.rule().ruleId(), match.rule().severity(), match.rule().message(),
						match.text()))
				.toList();
		return new ScanResult(problems, replaceWithPlaceholders(text, matches));
	}

	/** All matches of all rules, without overlaps, sorted by position. */
	private static List<Match> findMatches(String text) {
		List<Match> matches = new ArrayList<>();
		for (Rule rule : RULES) {
			Matcher matcher = rule.pattern().matcher(text);
			while (matcher.find()) {
				Match match = new Match(rule, matcher.start(), matcher.end(), matcher.group());
				if (rule.isMatch().test(match.text()) && matches.stream().noneMatch(match::overlaps)) {
					matches.add(match);
				}
			}
		}
		matches.sort(Comparator.comparingInt(Match::start));
		return matches;
	}

	/** Numbers placeholders per type in order of appearance: [EMAIL_1], [EMAIL_2], [IBAN_1] ... */
	private static String replaceWithPlaceholders(String text, List<Match> matches) {
		Map<String, Integer> counters = new HashMap<>();
		StringBuilder result = new StringBuilder();
		int position = 0;
		for (Match match : matches) {
			int number = counters.merge(match.rule().placeholder(), 1, Integer::sum);
			result.append(text, position, match.start())
					.append('[').append(match.rule().placeholder()).append('_').append(number).append(']');
			position = match.end();
		}
		return result.append(text.substring(position)).toString();
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

	/** One kind of sensitive data and how it is reported; {@code isMatch} is an optional extra check. */
	private record Rule(String ruleId, Severity severity, String message, String placeholder, Pattern pattern,
			Predicate<String> isMatch) {

		Rule(String ruleId, Severity severity, String message, String placeholder, Pattern pattern) {
			this(ruleId, severity, message, placeholder, pattern, match -> true);
		}
	}

	private record Match(Rule rule, int start, int end, String text) {

		boolean overlaps(Match other) {
			return start < other.end && end > other.start;
		}
	}
}
