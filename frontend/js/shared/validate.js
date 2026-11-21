// Validators for the API payloads. The contract is defined in API.md; keep both in sync.
// Each validator returns the payload if it is valid and throws a ValidationError otherwise.

export const PROMPT_MAX_LENGTH = 20_000;
export const COMPLIANCE_MAX_LENGTH = 500_000;

/** Severities as in the compliance file; only "low" (a notice) still allows sending. */
export const SEVERITIES = ["critical", "high", "medium", "low"];
export const NOTICE_SEVERITY = "low";

const PROBLEM_FIELDS = ["ruleId", "severity", "message", "location"];

/** @typedef {{prompt: string, compliance: string}} CheckRequest */
/**
 * @typedef {{ruleId: string, severity: "critical" | "high" | "medium" | "low",
 *            message: string, location: string}} Problem
 */
/** @typedef {{isSafe: boolean, problems: Problem[], improvedVersion: string}} CheckResponse */

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

/** Length in UTF-16 code units, exactly like the backend (Java String.length()): an emoji counts as 2. */
export function charLength(value) {
  return value.length;
}

function checkText(obj, field, maxLength, errors) {
  const value = obj[field];
  if (!(field in obj)) errors.push(`"${field}" is missing.`);
  else if (typeof value !== "string") errors.push(`"${field}" must be a string.`);
  else if (value.trim() === "") errors.push(`"${field}" must not be empty.`);
  else if (charLength(value) > maxLength) errors.push(`"${field}" must be at most ${maxLength} characters long.`);
}

/** A problem blocks the prompt unless it is only a notice. */
export function isBlocking(problem) {
  return problem.severity !== NOTICE_SEVERITY;
}

/** Each problem: {ruleId, severity, message, location}; location may be "" (no specific passage). */
function checkProblem(problem, i, errors) {
  const at = `problems[${i}]`;
  if (!isPlainObject(problem)) {
    errors.push(`"${at}" must be an object with ${PROBLEM_FIELDS.join(", ")}.`);
    return;
  }
  checkOnlyFields(problem, PROBLEM_FIELDS, errors, `${at}.`);
  for (const field of ["ruleId", "message"]) {
    if (typeof problem[field] !== "string" || problem[field].trim() === "") {
      errors.push(`"${at}.${field}" must be a non-empty string.`);
    }
  }
  if (!SEVERITIES.includes(problem.severity)) {
    errors.push(`"${at}.severity" must be one of ${SEVERITIES.map((s) => `"${s}"`).join(", ")}.`);
  }
  if (typeof problem.location !== "string") errors.push(`"${at}.location" must be a string ("" if none).`);
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
  checkOnlyFields(payload, ["isSafe", "problems", "improvedVersion"], errors);

  if (typeof payload.isSafe !== "boolean") errors.push(`"isSafe" must be a boolean.`);

  const { problems } = payload;
  if (!Array.isArray(problems)) {
    errors.push(`"problems" must be an array of objects.`);
  } else {
    const countBefore = errors.length;
    problems.forEach((problem, i) => checkProblem(problem, i, errors));
    const problemsValid = errors.length === countBefore;
    if (problemsValid && typeof payload.isSafe === "boolean" && payload.isSafe !== !problems.some(isBlocking)) {
      errors.push(`"isSafe" must be true exactly when no problem has severity "critical", "high" or "medium".`);
    }
  }

  if (typeof payload.improvedVersion !== "string") errors.push(`"improvedVersion" must be a string ("" if none).`);

  if (errors.length) throw new ValidationError("response", errors);
  return /** @type {CheckResponse} */ (payload);
}
