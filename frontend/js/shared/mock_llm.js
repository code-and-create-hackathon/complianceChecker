// Demo mode: simulates the check service locally in the browser until a backend exists.
// Response format as in API.md, including the optional "findings" field. No data is sent anywhere.

const DELAY_MS = 900;

// Order matters: specific patterns (IBAN) before general ones (long digit sequences).
// Overlapping matches belong to the pattern listed first.
// severity: "crit" = violation, "warn" = notice. ruleId as in Testdata/compliance-guide-musterfinanz.md.
const CHECKS = [
  {
    pattern: /\b[A-Z]{2}\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\b/g,
    ruleId: "R-02",
    category: "IBAN",
    severity: "crit",
    problem: "Contains an IBAN. Bank details must not be sent to AI systems.",
    placeholder: "IBAN",
  },
  {
    pattern: /\b\d(?:[ -]?\d){12,18}\b/g,
    ruleId: "R-02",
    category: "Card or account number",
    severity: "crit",
    problem: "Contains a possible card or account number.",
    placeholder: "NUMBER",
  },
  {
    pattern: /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g,
    ruleId: "R-01",
    category: "Email address",
    severity: "crit",
    problem: "Contains an email address.",
    placeholder: "EMAIL",
  },
  {
    // Only the number itself is the finding; the label in front of it stays.
    pattern:
      /(?<=\b(?:(?:Kunden|Konto|Vertrags|Depot)(?:nummer|nr\.?)|(?:customer|client|account|contract) (?:number|no\.?))\s*:?\s*)[A-Z0-9-]{4,}/gi,
    ruleId: "R-02",
    category: "Internal number",
    severity: "crit",
    problem: "Contains an internal customer, account, contract or custody account number.",
    placeholder: "NUMBER",
  },
  {
    // incl. inflections: vertrauliche, vertraulichen, Insiderinformationen …
    pattern: /\b(?:strictly confidential|confidential|streng vertraulich\w*|vertraulich\w*|insider\w*)\b/gi,
    ruleId: "R-09",
    category: "Confidentiality marking",
    severity: "warn",
    problem: "Contains information marked as confidential.",
    placeholder: "REMOVED",
  },
];

/** All matches with their position in the original prompt, without overlaps, sorted by position. */
function collectMatches(prompt) {
  const taken = [];
  const overlaps = (start, end) => taken.some((m) => start < m.end && end > m.start);

  for (const check of CHECKS) {
    for (const match of prompt.matchAll(check.pattern)) {
      const start = match.index;
      const end = start + match[0].length;
      if (overlaps(start, end)) continue;
      taken.push({ check, start, end, text: match[0] });
    }
  }
  return taken.sort((a, b) => a.start - b.start);
}

/**
 * @param {string} prompt
 * @param {string} _compliance  Ignored in demo mode.
 * @returns {Promise<import("./validate.js").CheckResponse>}
 */
export async function mockLlm(prompt, _compliance) {
  await new Promise((resolve) => setTimeout(resolve, DELAY_MS));

  const matches = collectMatches(prompt);
  if (matches.length === 0) {
    return { isSafe: true, problems: [], improvedVersion: "", findings: [] };
  }

  // Number the placeholders per type: [IBAN_1], [EMAIL_1], [EMAIL_2] …
  const counters = {};
  const findings = matches.map((m, index) => {
    counters[m.check.placeholder] = (counters[m.check.placeholder] || 0) + 1;
    return {
      id: index + 1,
      ruleId: m.check.ruleId,
      category: m.check.category,
      severity: m.check.severity,
      message: m.check.problem,
      match: m.text,
      start: m.start,
      end: m.end,
      replacement: `[${m.check.placeholder}_${counters[m.check.placeholder]}]`,
    };
  });

  // Sanitised version: replace from the back so the positions stay valid.
  let improvedVersion = prompt;
  for (const f of [...findings].reverse()) {
    improvedVersion = improvedVersion.slice(0, f.start) + f.replacement + improvedVersion.slice(f.end);
  }

  // problems: one message per rule type, with the number of matches.
  const problems = [];
  for (const check of CHECKS) {
    const count = matches.filter((m) => m.check === check).length;
    if (count === 0) continue;
    problems.push(count > 1 ? `${check.problem.replace(/\.$/, "")} (${count} matches).` : check.problem);
  }

  return { isSafe: false, problems, improvedVersion: improvedVersion.trim(), findings };
}
