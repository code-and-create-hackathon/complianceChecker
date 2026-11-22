package com.compliancechecker.service;

import com.compliancechecker.model.Problem;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the category of a rule in the compliance file itself: the name the file gives the rule, i.e. the line with
 * its id without the id, the severity and Markdown formatting. {@code ### R-01 Personal data of customers (CRITICAL)}
 * gives {@code Personal data of customers}. Nothing here is specific to one compliance file.
 */
public final class RuleCategories {

	/** Markdown heading or list marks, emphasis and separators around the name. */
	private static final Pattern DECORATION = Pattern.compile("^[\\s#>*_`|:.\\-–—]+|[\\s*_`|:.\\-–—]+$");
	/** A trailing "(CRITICAL)", "[high]", "- Severity: medium" and the like. */
	private static final Pattern SEVERITY = Pattern.compile(
			"[\\s(\\[,;:|\\-–—]*(?:severity\\s*[:=]?\\s*)?(?:critical|high|medium|low)[)\\]]?\\s*$",
			Pattern.CASE_INSENSITIVE);

	private RuleCategories() {
	}

	/** The category of {@code ruleId} as named in {@code rules}, or {@code ""} if the file names none. */
	public static String categoryFor(String rules, String ruleId) {
		if (rules == null || ruleId == null || ruleId.isBlank()) {
			return "";
		}
		Pattern idInLine = Pattern.compile("(?<![\\w-])" + Pattern.quote(ruleId) + "(?![\\w-])");
		for (String line : rules.split("\\R")) {
			Matcher matcher = idInLine.matcher(line);
			if (!matcher.find()) {
				continue;
			}
			String name = DECORATION.matcher(line.substring(matcher.end())).replaceAll("");
			name = SEVERITY.matcher(name).replaceFirst("");
			name = DECORATION.matcher(name).replaceAll("").strip();
			if (!name.isEmpty() && name.length() <= 80) {
				return name;
			}
		}
		return "";
	}

	/** Problems without a category get the one the compliance file gives their rule. */
	public static List<Problem> fillMissing(List<Problem> problems, String rules) {
		return problems.stream()
				.map(p -> !p.category().isEmpty() ? p
						: new Problem(p.ruleId(), categoryFor(rules, p.ruleId()), p.severity(), p.message(), p.location()))
				.toList();
	}
}
