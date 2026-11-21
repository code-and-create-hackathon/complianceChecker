// Result panel: states (empty, loading, status, error), counts, findings list and the hidden messages.
import { isMockMode } from "./mock_mode.js";
import { isBlocking } from "../shared/validate.js";
import { $, announce } from "./dom.js";
import { plural } from "../shared/format.js";
import { state } from "./state.js";

const result = $("result");
const states = result.querySelectorAll("[data-state]");
const errorText = $("error");

// Div 1: status (visible)
const statusBox = $("result-status");
const statusTitle = $("status-title");
const statusText = $("status-text");
const safeActions = $("safe-actions");
const suggestionActions = $("suggestion-actions");

// Counts and findings list (visible)
const counts = $("result-counts");
const countViolations = $("count-violations");
const countNotices = $("count-notices");
const findingsBlock = $("findings-block");
const findingList = $("result-findings");

// Div 2: messages (hidden, for developers)
const messages = $("result-messages");
const messageProblems = $("result-problems");
const messageImproved = $("result-improved-text");
const messageRaw = $("message-raw");

const SEVERITY_LABELS = { critical: "Critical", high: "High", medium: "Medium", low: "Low" };

function element(tag, className, text) {
  const el = document.createElement(tag);
  el.className = className;
  el.textContent = text;
  return el;
}

/** Visible findings: one entry per problem. Violations red, notices ("low") brown. */
function renderFindings(problems) {
  findingList.replaceChildren(
    ...problems.map((p, index) => {
      const li = element("li", `finding ${isBlocking(p) ? "is-crit" : "is-warn"}`, "");
      const head = element("span", "finding-head", "");
      head.append(
        element("span", "finding-rule", p.ruleId),
        element("span", "severity-badge", SEVERITY_LABELS[p.severity] ?? p.severity),
      );
      const body = element("span", "finding-body", "");
      body.append(head);
      if (p.location) body.append(element("span", "finding-location", `“${p.location}”`));
      body.append(element("span", "finding-message", p.message));
      li.append(element("span", "finding-num", String(index + 1)), body);
      return li;
    }),
  );
  findingsBlock.hidden = problems.length === 0;

  const violations = problems.filter(isBlocking).length;
  const notices = problems.length - violations;
  countViolations.textContent = plural(violations, "violation", "violations");
  countNotices.textContent = plural(notices, "notice", "notices");
  countViolations.hidden = violations === 0;
  countNotices.hidden = notices === 0;
  counts.hidden = problems.length === 0;
}

/** @param {"empty" | "loading" | "status" | "error"} name */
export function setState(name) {
  states.forEach((el) => {
    el.hidden = el.dataset.state !== name;
  });
  result.setAttribute("aria-busy", String(name === "loading"));
}

/**
 * Div 2: store the messages from the API response. Always stays hidden.
 * @returns {import("../shared/validate.js").Problem[]}
 */
function fillMessages(data) {
  const problems = Array.isArray(data?.problems) ? data.problems : [];
  messages.dataset.isSafe = data ? String(Boolean(data.isSafe)) : "";
  messageProblems.replaceChildren(
    ...problems.map((p) => {
      const li = document.createElement("li");
      li.textContent = [p.ruleId, p.severity, p.message, p.location && `"${p.location}"`].filter(Boolean).join(" · ");
      return li;
    }),
  );
  messageImproved.textContent = (data?.improvedVersion || "").trim();
  messageRaw.textContent = data ? JSON.stringify(data, null, 2) : "";
  renderFindings(problems);
  return problems;
}

/** Div 1: show the status. Notices (severity "low") do not block sending. */
function showStatus(isSafe, violations, notices, hasSuggestion) {
  statusBox.classList.toggle("is-safe", isSafe);
  statusBox.classList.toggle("is-risk", !isSafe);

  if (isSafe) {
    statusTitle.textContent = "No violations found";
    statusText.textContent = notices
      ? `${plural(notices, "notice", "notices")} found. The prompt can be sent.`
      : "The prompt can be sent.";
  } else {
    statusTitle.textContent = violations ? `${plural(violations, "violation", "violations")} found` : "Risk detected";
    statusText.textContent = "Please revise the prompt before sending.";
  }

  safeActions.hidden = !isSafe;
  suggestionActions.hidden = isSafe || !hasSuggestion;
  setState("status");
  announce(`Result: ${statusTitle.textContent}.`);
}

export function showLoading() {
  fillMessages(null);
  setState("loading");
  announce("Checking.");
}

/** The improved prompt from the result on screen ("" if none). */
export function getSuggestion() {
  return state.result?.response.improvedVersion.trim() ?? "";
}

/** Discards the last result as soon as the rules or the prompt change (site.js handles running checks). */
export function resetResult() {
  if (state.busy) return;
  state.result = null;
  fillMessages(null);
  setState("empty");
}

/** Shows `state.result`, which site.js sets before calling this. */
export function renderResult() {
  const data = state.result.response;
  const problems = fillMessages(data);
  const violations = problems.filter(isBlocking).length;
  showStatus(Boolean(data.isSafe), violations, problems.length - violations, Boolean(getSuggestion()));
  document.dispatchEvent(new CustomEvent("compliance:result", { detail: data }));
}

export function renderError(error) {
  let message = error.message || "Unknown error.";
  if (!isMockMode()) {
    message += " Without a backend, open the page with ?mock=1 to use demo mode.";
  }
  errorText.textContent = message;
  setState("error");
  announce(`Check failed. ${message}`);
}
