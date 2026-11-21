// Validators for the API payloads. The contract is defined in API.md; keep both in sync.
// Each validator returns the payload if it is valid and throws a ValidationError otherwise.

export const PROMPT_MAX_LENGTH = 20_000;
export const COMPLIANCE_MAX_LENGTH = 500_000;

const PROBLEM_FIELDS = ["ruleId", "category", "severity", "message", "location"];

export function isBlocking(problem) {
  return problem.severity !== "low";
}

/** @typedef {{prompt: string, compliance: string}} CheckRequest */
/**
 * @typedef {{ruleId: string, category: string}} Problem
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

/** Each problem identifies a violated compliance rule and its top-level category. */
function checkProblem(problem, i, errors) {
  const at = `problems[${i}]`;
  if (!isPlainObject(problem)) {
    errors.push(`"${at}" must be an object with ${PROBLEM_FIELDS.join(", ")}.`);
    return;
  }
  checkOnlyFields(problem, PROBLEM_FIELDS, errors, `${at}.`);
  const detailed = "severity" in problem || "message" in problem || "location" in problem;
  for (const field of detailed ? ["ruleId", "severity", "message"] : ["ruleId", "category"]) {
    if (typeof problem[field] !== "string" || problem[field].trim() === "") {
      errors.push(`"${at}.${field}" must be a non-empty string.`);
    }
  }
  if (detailed && !["critical", "high", "medium", "low"].includes(problem.severity)) {
    errors.push(`"${at}.severity" is invalid.`);
  }
  if (detailed && typeof problem.location !== "string") errors.push(`"${at}.location" must be a string.`);
  if ("category" in problem && (typeof problem.category !== "string" || !problem.category.trim())) {
    errors.push(`"${at}.category" must be a non-empty string.`);
  }
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
      errors.push(`"isSafe" must match the blocking problems.`);
    }
  }

  if (typeof payload.improvedVersion !== "string") errors.push(`"improvedVersion" must be a string ("" if none).`);

  if (errors.length) throw new ValidationError("response", errors);
  return /** @type {CheckResponse} */ (payload);
}
