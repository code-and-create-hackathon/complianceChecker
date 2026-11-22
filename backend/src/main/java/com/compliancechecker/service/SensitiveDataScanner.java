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
 * <p>Used by the Bedrock client to replace sensitive values before the prompt leaves the backend, and by the mock LLM
 * as its whole check. It only knows the patterns below and is not a complete compliance check. Rule ids and
 * severities follow {@code Testdata/compliance-guide-musterfinanz.md}.
 */
@Component
public class SensitiveDataScanner {

	private static final String UPPER_WORD = "[A-ZÄÖÜ][a-zäöüß]+";

	/** Order matters: when matches overlap, the rule listed first wins. */
	private static final List<Rule> RULES = List.of(
			// Sentence rules first, so such a sentence is removed as a whole, including anything inside it.
			Rule.sentence("R-04", Severity.CRITICAL, "Contains possible insider information.", "REMOVED",
					"profit warning|ad[ -]hoc (?:disclosure|announcement|notification|release)|Ad-hoc-Mitteilung"
							+ "|takeover (?:bid|offer)|not yet (?:public|announced)"
							+ "|before (?:the )?announcement|insider\\w*|Gewinnwarnung|Übernahmeangebot"),
			Rule.sentence("R-03", Severity.CRITICAL, "Contains health data (special category of personal data).", "REMOVED",
					"sick leave|sick note|diagnosis|diagnosed|depression|burn-?out|cancer|pregnan\\w*"
							+ "|disabilit\\w*|krankgeschrieben|Krankschreibung|Diagnose|schwanger\\w*"),
			Rule.sentence("R-09", Severity.MEDIUM, "Contains information marked as confidential.", "REMOVED",
					"confidential|internal only|not public|vertraulich\\w*|nur intern|nicht öffentlich"),
			new Rule("R-05", Severity.CRITICAL, "Contains a password or access key.", "SECRET",
					// Only the value; a value that already is a placeholder is left alone.
					Pattern.compile("(?<=\\b(?:password|passwd|pwd|passwort|secret|token|api[_-]?key)\\s{0,2}[=:]\\s{0,2})"
							+ "(?!\\[)(?:\"[^\"\\n]*\"|'[^'\\n]*'|[^\\s,;)]+)", Pattern.CASE_INSENSITIVE)),
			new Rule("R-05", Severity.CRITICAL, "Contains an internal IP address.", "IP_ADDRESS",
					Pattern.compile("\\b(?:10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})\\b")),
			new Rule("R-02", Severity.CRITICAL, "Contains an IBAN. Bank details must not be sent to AI systems.", "IBAN",
					Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?\\b")),
			new Rule("R-02", Severity.CRITICAL, "Contains a credit card number.", "CARD_NUMBER",
					Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)"), SensitiveDataScanner::passesLuhnCheck),
			new Rule("R-01", Severity.CRITICAL, "Contains an email address.", "EMAIL",
					Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
			new Rule("R-01", Severity.CRITICAL, "Contains a date of birth.", "DATE_OF_BIRTH",
					Pattern.compile("(?<=\\b(?:born|born on|date of birth:?|DOB:?|geb\\.|geboren|geboren am)\\s{1,2})"
							+ "\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}", Pattern.CASE_INSENSITIVE)),
			new Rule("R-01", Severity.CRITICAL, "Contains a postal address.", "ADDRESS",
					// Street with house number, optionally followed by postcode and city. A postcode alone is not
					// matched, because "50000 Euro" looks the same.
					Pattern.compile("\\b" + UPPER_WORD + "(?:straße|strasse|str\\.|weg|allee|platz|gasse|ring)\\s+\\d+[a-z]?"
							+ "(?:,\\s*\\d{5}\\s+" + UPPER_WORD + ")?")),
			new Rule("R-01", Severity.CRITICAL, "Contains a person's name.", "NAME",
					// Only names after a form of address, so ordinary capitalised words are not matched.
					Pattern.compile("(?<=\\b(?:Mr|Mrs|Ms|Mr\\.|Mrs\\.|Ms\\.|Herr|Frau|Herrn)\\s)" + UPPER_WORD
							+ "(?:\\s" + UPPER_WORD + ")?")),
			new Rule("R-01", Severity.CRITICAL, "Contains a phone number.", "PHONE",
					// Starts with +, 00 or 0, so ordinary numbers like amounts or years are not matched.
					Pattern.compile("(?<![\\w+])(?:(?:\\+|00)[1-9]\\d{0,2}|0)[ /-]?\\(?\\d{2,5}\\)?(?:[ /-]?\\d{2,}){1,4}(?!\\w)")),
			new Rule("R-08", Severity.MEDIUM, "Contains the name of a company (possible client or business partner).",
					"COMPANY",
					// Only names with a legal form, so well-known brands like "Deutsche Bank" stay allowed.
					Pattern.compile("\\b(?:[A-ZÄÖÜ][\\wäöüß&-]*\\s+){0,3}[A-ZÄÖÜ][\\wäöüß&-]*\\s+"
							+ "(?:GmbH & Co\\. KG|GmbH|AG|SE|KGaA|KG|OHG|UG|Ltd\\.?|Inc\\.?|LLC|plc)(?![\\wäöüß])")));

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
				int start = matcher.start();
				int end = matcher.end();
				if (rule.wholeSentence()) {
					start = sentenceStart(text, start);
					end = sentenceEnd(text, end);
				}
				Match match = new Match(rule, start, end, text.substring(start, end));
				if (rule.isMatch().test(match.text()) && matches.stream().noneMatch(match::overlaps)) {
					matches.add(match);
				}
			}
		}
		matches.sort(Comparator.comparingInt(Match::start));
		return matches;
	}

	/**
	 * Start of the sentence that contains {@code index}, without leading whitespace. Done in code, not with a regex:
	 * a regex with a repeated alternative overflows the stack on sentences of a few thousand characters.
	 */
	private static int sentenceStart(String text, int index) {
		int start = index;
		while (start > 0 && text.charAt(start - 1) != '\n' && !endsSentence(text, start - 1)) {
			start--;
		}
		while (start < index && Character.isWhitespace(text.charAt(start))) {
			start++;
		}
		return start;
	}

	/** End of the sentence that contains {@code index}: includes its closing ".", "!" or "?", not the line break. */
	private static int sentenceEnd(String text, int index) {
		for (int end = index; end < text.length(); end++) {
			if (text.charAt(end) == '\n') {
				return end;
			}
			if (endsSentence(text, end)) {
				return end + 1;
			}
		}
		return text.length();
	}

	/** A ".", "!" or "?" ends a sentence only if whitespace or the end follows, so "a.b@x.com" and "3.5" do not. */
	private static boolean endsSentence(String text, int index) {
		char c = text.charAt(index);
		return (c == '.' || c == '!' || c == '?')
				&& (index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1)));
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

	/**
	 * One kind of sensitive data and how it is reported. {@code isMatch} is an optional extra check;
	 * {@code wholeSentence} reports (and removes) the whole sentence around each match instead of just the match.
	 */
	private record Rule(String ruleId, Severity severity, String message, String placeholder, Pattern pattern,
			Predicate<String> isMatch, boolean wholeSentence) {

		Rule(String ruleId, Severity severity, String message, String placeholder, Pattern pattern) {
			this(ruleId, severity, message, placeholder, pattern, match -> true, false);
		}

		Rule(String ruleId, Severity severity, String message, String placeholder, Pattern pattern,
				Predicate<String> isMatch) {
			this(ruleId, severity, message, placeholder, pattern, isMatch, false);
		}

		/** A rule that removes the whole sentence around one of the keywords ({@code |}-separated regex parts). */
		static Rule sentence(String ruleId, Severity severity, String message, String placeholder, String keywords) {
			Pattern pattern = Pattern.compile("\\b(?:" + keywords + ")\\b",
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
			return new Rule(ruleId, severity, message, placeholder, pattern, match -> true, true);
		}
	}

	private record Match(Rule rule, int start, int end, String text) {

		boolean overlaps(Match other) {
			return start < other.end && end > other.start;
		}
	}
}
