// Validators for the API payloads. The contract is defined in API.md; keep both in sync.
// Each validator returns the payload if it is valid and throws a ValidationError otherwise.

export const PROMPT_MAX_LENGTH = 20_000;
export const COMPLIANCE_MAX_LENGTH = 500_000;

/** @typedef {{prompt: string, compliance: string}} CheckRequest */
/** @typedef {{isSafe: boolean, problems: string[], improvedVersion: string}} CheckResponse */

/** Thrown when a payload breaks the API contract. `errors` lists every violation. */
export class ValidationError extends Error {
  /** @param {string} what @param {string[]} errors */
  constructor(what, errors) {
    super(`Ungültige ${what}: ${errors.join(" ")}`);
    this.name = "ValidationError";
    this.errors = errors;
  }
}

function isPlainObject(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function checkOnlyFields(obj, allowed, errors) {
  for (const key of Object.keys(obj)) {
    if (!allowed.includes(key)) errors.push(`Unbekanntes Feld "${key}".`);
  }
}

/** Length in Unicode characters (code points), not UTF-16 code units: an emoji counts as 1. */
function charLength(value) {
  return [...value].length;
}

function checkText(obj, field, maxLength, errors) {
  const value = obj[field];
  if (!(field in obj)) errors.push(`"${field}" fehlt.`);
  else if (typeof value !== "string") errors.push(`"${field}" muss ein String sein.`);
  else if (value.trim() === "") errors.push(`"${field}" darf nicht leer sein.`);
  else if (charLength(value) > maxLength) errors.push(`"${field}" darf höchstens ${maxLength} Zeichen lang sein.`);
}

/**
 * Validates a client → server payload.
 * @param {unknown} payload
 * @returns {CheckRequest}
 * @throws {ValidationError}
 */
export function validateRequest(payload) {
  if (!isPlainObject(payload)) throw new ValidationError("Anfrage", ["Anfrage muss ein JSON-Objekt sein."]);

  const errors = [];
  checkOnlyFields(payload, ["prompt", "compliance"], errors);
  checkText(payload, "prompt", PROMPT_MAX_LENGTH, errors);
  checkText(payload, "compliance", COMPLIANCE_MAX_LENGTH, errors);

  if (errors.length) throw new ValidationError("Anfrage", errors);
  return /** @type {CheckRequest} */ (payload);
}

/**
 * Validates a server → client payload.
 * @param {unknown} payload
 * @returns {CheckResponse}
 * @throws {ValidationError}
 */
export function validateResponse(payload) {
  if (!isPlainObject(payload)) throw new ValidationError("Antwort", ["Antwort muss ein JSON-Objekt sein."]);

  const errors = [];
  checkOnlyFields(payload, ["isSafe", "problems", "improvedVersion"], errors);

  if (typeof payload.isSafe !== "boolean") errors.push(`"isSafe" muss ein Boolean sein.`);

  const { problems } = payload;
  if (!Array.isArray(problems)) {
    errors.push(`"problems" muss ein Array von Strings sein.`);
  } else {
    problems.forEach((p, i) => {
      if (typeof p !== "string" || p.trim() === "") errors.push(`"problems[${i}]" muss ein nicht-leerer String sein.`);
    });
    if (typeof payload.isSafe === "boolean" && payload.isSafe !== (problems.length === 0)) {
      errors.push(`"isSafe" muss genau dann true sein, wenn "problems" leer ist.`);
    }
  }

  if (typeof payload.improvedVersion !== "string") errors.push(`"improvedVersion" muss ein String sein ("" wenn keiner).`);

  if (errors.length) throw new ValidationError("Antwort", errors);
  return /** @type {CheckResponse} */ (payload);
}
