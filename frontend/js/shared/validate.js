// Validators for the API payloads. The contract is defined in API.md; keep both in sync.
// Each validator returns the payload if it is valid and throws a ValidationError otherwise.

export const PROMPT_MAX_LENGTH = 20_000;
export const COMPLIANCE_MAX_LENGTH = 500_000;

const SEVERITIES = ["crit", "warn"];
const FINDING_FIELDS = ["id", "ruleId", "category", "severity", "message", "match", "start", "end", "replacement"];
const FINDING_TEXT_FIELDS = ["ruleId", "category", "message", "match", "replacement"];

/** @typedef {{prompt: string, compliance: string}} CheckRequest */
/**
 * @typedef {{id: number, ruleId: string, category: string, severity: "crit" | "warn", message: string,
 *            match: string, start: number, end: number, replacement: string}} Finding
 */
/** @typedef {{isSafe: boolean, problems: string[], improvedVersion: string, findings?: Finding[]}} CheckResponse */

/** Thrown when a payload breaks the API contract. `errors` lists every violation. */
export class ValidationError extends Error {
  /** @param {string} what @param {string[]} errors */
  constructor(what, errors) {
    super(`Invalid ${what}: ${errors.join(" ")}`);
    this.name = "ValidationError";
    this.errors = errors;
  }
}

function isPlainObject(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function checkOnlyFields(obj, allowed, errors, prefix = "") {
  for (const key of Object.keys(obj)) {
    if (!allowed.includes(key)) errors.push(`Unknown field "${prefix}${key}".`);
  }
}

/** Length in Unicode characters (code points), not UTF-16 code units: an emoji counts as 1. */
export function charLength(value) {
  return [...value].length;
}

function checkText(obj, field, maxLength, errors) {
  const value = obj[field];
  if (!(field in obj)) errors.push(`"${field}" is missing.`);
  else if (typeof value !== "string") errors.push(`"${field}" must be a string.`);
  else if (value.trim() === "") errors.push(`"${field}" must not be empty.`);
  else if (charLength(value) > maxLength) errors.push(`"${field}" must be at most ${maxLength} characters long.`);
}

/** Optional "findings": one entry per match, with its position in the prompt. */
function checkFindings(findings, isSafe, errors) {
  if (!Array.isArray(findings)) {
    errors.push(`"findings" must be an array.`);
    return;
  }
  findings.forEach((f, i) => {
    const at = `findings[${i}]`;
    if (!isPlainObject(f)) {
      errors.push(`"${at}" must be an object.`);
      return;
    }
    checkOnlyFields(f, FINDING_FIELDS, errors, `${at}.`);
    if (!Number.isInteger(f.id) || f.id < 1) errors.push(`"${at}.id" must be a positive integer.`);
    if (!SEVERITIES.includes(f.severity)) errors.push(`"${at}.severity" must be "crit" or "warn".`);
    FINDING_TEXT_FIELDS.forEach((key) => {
      if (typeof f[key] !== "string") errors.push(`"${at}.${key}" must be a string.`);
    });
    if (!Number.isInteger(f.start) || !Number.isInteger(f.end) || f.start < 0 || f.end <= f.start) {
      errors.push(`"${at}.start" and "${at}.end" must be integers with 0 ≤ start < end.`);
    }
  });
  if (isSafe === true && findings.length) errors.push(`"findings" must be empty when "isSafe" is true.`);
}

/**
 * Validates a client → server payload.
 * @param {unknown} payload
 * @returns {CheckRequest}
 * @throws {ValidationError}
 */
export function validateRequest(payload) {
  if (!isPlainObject(payload)) throw new ValidationError("request", ["The request must be a JSON object."]);

  const errors = [];
  checkOnlyFields(payload, ["prompt", "compliance"], errors);
  checkText(payload, "prompt", PROMPT_MAX_LENGTH, errors);
  checkText(payload, "compliance", COMPLIANCE_MAX_LENGTH, errors);

  if (errors.length) throw new ValidationError("request", errors);
  return /** @type {CheckRequest} */ (payload);
}

/**
 * Validates a server → client payload.
 * @param {unknown} payload
 * @returns {CheckResponse}
 * @throws {ValidationError}
 */
export function validateResponse(payload) {
  if (!isPlainObject(payload)) throw new ValidationError("response", ["The response must be a JSON object."]);

  const errors = [];
  checkOnlyFields(payload, ["isSafe", "problems", "improvedVersion", "findings"], errors);

  if (typeof payload.isSafe !== "boolean") errors.push(`"isSafe" must be a boolean.`);

  const { problems } = payload;
  if (!Array.isArray(problems)) {
    errors.push(`"problems" must be an array of strings.`);
  } else {
    problems.forEach((p, i) => {
      if (typeof p !== "string" || p.trim() === "") errors.push(`"problems[${i}]" must be a non-empty string.`);
    });
    if (typeof payload.isSafe === "boolean" && payload.isSafe !== (problems.length === 0)) {
      errors.push(`"isSafe" must be true exactly when "problems" is empty.`);
    }
  }

  if (typeof payload.improvedVersion !== "string") errors.push(`"improvedVersion" must be a string ("" if none).`);

  if ("findings" in payload) checkFindings(payload.findings, payload.isSafe, errors);

  if (errors.length) throw new ValidationError("response", errors);
  return /** @type {CheckResponse} */ (payload);
}
