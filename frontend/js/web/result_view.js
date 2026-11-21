// Result card: states (empty, loading, status, error) and the hidden messages.
import { isMockMode } from "../shared/request_llm.js";
import { isBlocking } from "../shared/validate.js";
import { $, announce, plural } from "./dom.js";
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

// Div 2: messages (hidden, for developers)
const messages = $("result-messages");
const messageProblems = $("result-problems");
const messageImproved = $("result-improved-text");
const messageRaw = $("message-raw");

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

/** The improved prompt from the last result ("" if none). */
export function getSuggestion() {
  return messageImproved.textContent;
}

/** Discards the last result as soon as the rules or the prompt change. */
export function resetResult() {
  if (state.busy) return;
  fillMessages(null);
  setState("empty");
}

/** @param {import("../shared/validate.js").CheckResponse} data */
export function renderResult(data) {
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
