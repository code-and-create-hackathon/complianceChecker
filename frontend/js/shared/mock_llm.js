// Demo mode: simulates the check service locally in the browser until a backend exists.
// Response format as in API.md. No data is sent anywhere.
import { isBlocking } from "./validate.js";

const DELAY_MS = 900;

// Order matters: specific patterns (IBAN) before general ones (long digit sequences).
// Overlapping matches belong to the pattern listed first.
// ruleId and severity as in Testdata/compliance-guide-musterfinanz.md.
const CHECKS = [
  {
    pattern: /\b[A-Z]{2}\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\b/g,
    ruleId: "R-02",
    severity: "critical",
    message: "Contains an IBAN. Bank details must not be sent to AI systems.",
    placeholder: "IBAN",
  },
  {
    pattern: /\b\d(?:[ -]?\d){12,18}\b/g,
    ruleId: "R-02",
    severity: "critical",
    message: "Contains a possible card or account number.",
    placeholder: "NUMBER",
  },
  {
    pattern: /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g,
    ruleId: "R-01",
    severity: "critical",
    message: "Contains an email address.",
    placeholder: "EMAIL",
  },
  {
    // Only the number itself is the match; the label in front of it stays.
    pattern:
      /(?<=\b(?:(?:Kunden|Konto|Vertrags|Depot)(?:nummer|nr\.?)|(?:customer|client|account|contract) (?:number|no\.?))\s*:?\s*)[A-Z0-9-]{4,}/gi,
    ruleId: "R-02",
    severity: "critical",
    message: "Contains an internal customer, account, contract or custody account number.",
    placeholder: "NUMBER",
  },
  {
    // incl. inflections: vertrauliche, vertraulichen, Insiderinformationen …
    pattern: /\b(?:strictly confidential|confidential|streng vertraulich\w*|vertraulich\w*|insider\w*)\b/gi,
    ruleId: "R-09",
    severity: "medium",
    message: "Contains information marked as confidential.",
    placeholder: "REMOVED",
  },
];

/** All matches with their position in the prompt, without overlaps, sorted by position. */
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

/** Replaces the matches with numbered placeholders per type: [IBAN_1], [EMAIL_1], [EMAIL_2] … */
function sanitise(prompt, matches) {
  const counters = {};
  const numbered = matches.map((m) => {
    counters[m.check.placeholder] = (counters[m.check.placeholder] || 0) + 1;
    return { ...m, replacement: `[${m.check.placeholder}_${counters[m.check.placeholder]}]` };
  });
  let text = prompt;
  for (const m of numbered.reverse()) text = text.slice(0, m.start) + m.replacement + text.slice(m.end);
  return text.trim();
}

/**
 * @param {string} prompt
 * @param {string} _compliance  Ignored in demo mode.
 * @returns {Promise<import("./validate.js").CheckResponse>}
 */
export async function mockLlm(prompt, _compliance) {
  await new Promise((resolve) => setTimeout(resolve, DELAY_MS));

  const matches = collectMatches(prompt);
  const problems = matches.map((m) => ({
    ruleId: m.check.ruleId,
    severity: m.check.severity,
    message: m.check.message,
    location: m.text,
  }));
  const isSafe = !problems.some(isBlocking);

  return { isSafe, problems, improvedVersion: isSafe ? "" : sanitise(prompt, matches) };
}
