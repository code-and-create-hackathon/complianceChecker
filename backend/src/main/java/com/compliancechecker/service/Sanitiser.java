package com.compliancechecker.service;

import com.compliancechecker.model.Problem;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the sanitised version from the reported passages, so the model cannot reword the rest of the prompt, and
 * cleans up the model's findings. Generic: nothing here depends on a specific compliance file.
 */
public final class Sanitiser {

	/** A token that can be part of a number-like value: digits, capitals and separators, e.g. {@code DE89}, {@code 3704-0044}. */
	private static final Pattern VALUE_TOKEN = Pattern.compile("[0-9A-Z./-]+");
	private static final Pattern DIGIT = Pattern.compile("\\d");
	private static final String TRAILING_PUNCTUATION = ".,;:!?)";

	private Sanitiser() {
	}

	/**
	 * People type values with odd spacing ({@code D E 8 9 3704-0044 ...}) and the model sometimes marks only a part.
	 * A passage with at least four digits is widened over neighbouring tokens that belong to the same value: tokens of
	 * digits, capitals and separators that contain a digit or are at most two characters long. Words such as "IBAN" or
	 * "his" stop the widening.
	 */
	public static String widen(String text, String location) {
		if (location.isEmpty() || DIGIT.matcher(location).results().count() < 4) {
			return location;
		}
		int start = text.indexOf(location);
		if (start < 0) {
			return location;
		}
		int end = start + location.length();
		while (true) {
			int tokenEnd = start;
			while (tokenEnd > 0 && text.charAt(tokenEnd - 1) == ' ') {
				tokenEnd--;
			}
			int tokenStart = tokenEnd;
			while (tokenStart > 0 && text.charAt(tokenStart - 1) != ' ' && !Character.isWhitespace(text.charAt(tokenStart - 1))) {
				tokenStart--;
			}
			if (tokenStart == tokenEnd || !belongsToValue(text.substring(tokenStart, tokenEnd))) {
				break;
			}
			start = tokenStart;
		}
		while (true) {
			int tokenStart = end;
			while (tokenStart < text.length() && text.charAt(tokenStart) == ' ') {
				tokenStart++;
			}
			int tokenEnd = tokenStart;
			while (tokenEnd < text.length() && !Character.isWhitespace(text.charAt(tokenEnd))) {
				tokenEnd++;
			}
			String token = stripTrailingPunctuation(text.substring(tokenStart, tokenEnd));
			if (token.isEmpty() || !belongsToValue(token)) {
				break;
			}
			end = tokenStart + token.length();
		}
		return text.substring(start, end);
	}

	private static boolean belongsToValue(String token) {
		return VALUE_TOKEN.matcher(token).matches() && (DIGIT.matcher(token).find() || token.length() <= 2);
	}

	private static String stripTrailingPunctuation(String token) {
		int end = token.length();
		while (end > 0 && TRAILING_PUNCTUATION.indexOf(token.charAt(end - 1)) >= 0) {
			end--;
		}
		return token.substring(0, end);
	}

	/**
	 * Removes exact duplicates (same rule, same passage) and entries without a passage when the same rule is already
	 * reported with one. A rule reported only without a passage is kept.
	 */
	public static List<Problem> deduplicate(List<Problem> problems) {
		Set<String> rulesWithPassage = new LinkedHashSet<>();
		for (Problem p : problems) {
			if (!p.location().isEmpty()) {
				rulesWithPassage.add(p.ruleId());
			}
		}
		Set<String> seen = new LinkedHashSet<>();
		List<Problem> result = new ArrayList<>();
		for (Problem p : problems) {
			if (p.location().isEmpty() && rulesWithPassage.contains(p.ruleId())) {
				continue;
			}
			if (seen.add(p.ruleId() + "\u0000" + p.location() + "\u0000" + p.severity())) {
				result.add(p);
			}
		}
		return result;
	}

	/**
	 * Replaces every reported passage in {@code text} by a placeholder made from its category, e.g. the category
	 * {@code Personal data of customers} gives {@code [PERSONAL_DATA_OF_CUSTOMERS_1]}. The same value gets the same
	 * placeholder; everything else stays character for character. Longer passages are replaced first.
	 */
	public static String replacePassages(String text, List<Problem> problems) {
		Map<String, String> placeholderByValue = new HashMap<>();
		Map<String, Integer> counters = new HashMap<>();
		List<Problem> ordered = problems.stream()
				.filter(p -> !p.location().isBlank() && text.contains(p.location()))
				.sorted((a, b) -> Integer.compare(text.indexOf(a.location()), text.indexOf(b.location())))
				.toList();
		for (Problem p : ordered) {
			placeholderByValue.computeIfAbsent(p.location(), value -> {
				String name = placeholderName(p.category());
				return "[" + name + "_" + counters.merge(name, 1, Integer::sum) + "]";
			});
		}
		String result = text;
		List<String> values = new ArrayList<>(placeholderByValue.keySet());
		values.sort((a, b) -> Integer.compare(b.length(), a.length()));
		for (String value : values) {
			result = result.replace(value, placeholderByValue.get(value));
		}
		return result;
	}

	/** "Personal data of customers" becomes PERSONAL_DATA_OF_CUSTOMERS; no category gives REMOVED. */
	static String placeholderName(String category) {
		String ascii = Normalizer.normalize(category == null ? "" : category, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		String name = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		return name.isEmpty() ? "REMOVED" : name;
	}
}
