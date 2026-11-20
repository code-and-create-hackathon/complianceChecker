// Demo-Modus: simuliert den Prüfdienst lokal im Browser, solange es kein Backend gibt.
// Antwortformat wie in API.md. Es werden keine Daten übertragen.

const DELAY_MS = 900;

// Reihenfolge ist wichtig: spezifische Muster (IBAN) vor allgemeinen (lange Ziffernfolgen).
const CHECKS = [
  {
    pattern: /\b[A-Z]{2}\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\b/g,
    problem: "Enthält eine IBAN. Bankverbindungen dürfen nicht an KI-Systeme übermittelt werden.",
    replacement: "[IBAN]",
  },
  {
    pattern: /\b\d(?:[ -]?\d){12,18}\b/g,
    problem: "Enthält eine mögliche Karten- oder Kontonummer.",
    replacement: "[NUMMER]",
  },
  {
    pattern: /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g,
    problem: "Enthält eine E-Mail-Adresse.",
    replacement: "[E-MAIL]",
  },
  {
    pattern: /(\b(?:Kunden|Konto|Vertrags|Depot)(?:nummer|nr\.?)\s*:?\s*)[A-Z0-9-]{4,}/gi,
    problem: "Enthält eine interne Kunden-, Konto-, Vertrags- oder Depotnummer.",
    replacement: "$1[ENTFERNT]",
  },
  {
    // inkl. Beugungen: vertrauliche, vertraulichen, Insiderinformationen …
    pattern: /\b(?:streng vertraulich\w*|vertraulich\w*|confidential|insider\w*)\b/gi,
    problem: "Enthält als vertraulich gekennzeichnete Informationen.",
    replacement: "[ENTFERNT]",
  },
];

/**
 * @param {string} prompt
 * @param {string} _compliance  Wird im Demo-Modus nicht ausgewertet.
 * @returns {Promise<{isSafe: boolean, problems: string[], improvedVersion: string}>}
 */
export async function mockLlm(prompt, _compliance) {
  await new Promise((resolve) => setTimeout(resolve, DELAY_MS));

  const problems = [];
  let improvedVersion = prompt;

  for (const { pattern, problem, replacement } of CHECKS) {
    const matches = improvedVersion.match(pattern);
    if (!matches) continue;
    problems.push(
      matches.length > 1 ? `${problem.replace(/\.$/, "")} (${matches.length} Fundstellen).` : problem,
    );
    improvedVersion = improvedVersion.replace(pattern, replacement);
  }

  const isSafe = problems.length === 0;
  return { isSafe, problems, improvedVersion: isSafe ? "" : improvedVersion };
}
