// Result panel: verdict (empty, loading, status, error), findings list, sanitise & send,
// plus the hidden developer messages.
import { isMockMode } from "../shared/request_llm.js";
import { $, announce, plural } from "./dom.js";
import { state } from "./state.js";
import { showReader, showEditor, isReading, setActiveMark } from "./reader.js";

const result = $("result");
const states = result.querySelectorAll("[data-state]");
const errorText = $("error");
const subtitle = $("subtitle");
const SUBTITLE_INITIAL = subtitle.textContent;
const checkState = $("check-state");

// Div 1: status (visible)
const statusBox = $("result-status");
const statusTitle = $("status-title");
const statusText = $("status-text");
const stats = $("stats");
const statCrit = $("stat-crit");
const statWarn = $("stat-warn");

// Findings and sanitise & send (visible)
const findingsSection = $("findings-section");
const findingList = $("finding-list");
const sanitizeSection = $("sanitize-section");
const safeActions = $("safe-actions");
const suggestionActions = $("suggestion-actions");
const noSuggestion = $("no-suggestion");
const sendNote = $("send-note");

// Div 2: messages (hidden, for developers)
const messages = $("result-messages");
const messageProblems = $("result-problems");
const messageImproved = $("result-improved-text");
const messageRaw = $("message-raw");

const PLACEHOLDER = /^\[[^\]\s]+\]$/;

/** Prompt and findings of the last result, for the read view. */
let last = { prompt: "", findings: [] };

/** @param {"empty" | "loading" | "status" | "error"} name */
export function setState(name) {
  states.forEach((el) => {
    el.hidden = el.dataset.state !== name;
  });
  result.setAttribute("aria-busy", String(name === "loading"));
  if (name !== "status") {
    findingsSection.hidden = true;
    sanitizeSection.hidden = true;
  }
  const labels = { empty: "Not checked", loading: "Checking …", error: "Check failed" };
  if (labels[name]) checkState.textContent = labels[name];
}

/** The suggestion with its placeholders highlighted; textContent stays the plain text. */
function renderSuggestion(text) {
  messageImproved.replaceChildren(
    ...text
      .split(/(\[[^\]\s]+\])/)
      .filter(Boolean)
      .map((part) => {
        if (!PLACEHOLDER.test(part)) return document.createTextNode(part);
        const span = document.createElement("span");
        span.className = "placeholder";
        span.textContent = part;
        return span;
      }),
  );
}

/** Div 2: store the messages from the API response. Always stays hidden. */
function fillMessages(data) {
  const problems = Array.isArray(data?.problems) ? data.problems.filter(Boolean) : [];
  messages.dataset.isSafe = data ? String(Boolean(data.isSafe)) : "";
  messageProblems.replaceChildren(
    ...problems.map((problem) => {
      const li = document.createElement("li");
      li.textContent = problem;
      return li;
    }),
  );
  renderSuggestion((data?.improvedVersion || "").trim());
  messageRaw.textContent = data ? JSON.stringify(data, null, 2) : "";
  return problems;
}

function element(tag, className, text) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== undefined) el.textContent = text;
  return el;
}

/** One list entry per finding; without findings (older backend) one per problem. */
function renderFindings(findings, problems) {
  const items = findings.length
    ? findings.map((f) => {
        const severity = f.severity === "warn" ? "is-warn" : "is-crit";
        const button = element("button", `finding ${severity}`);
        button.type = "button";
        button.dataset.id = String(f.id);
        button.setAttribute("aria-pressed", "false");

        const head = element("span", "finding-head");
        head.append(
          element("span", "finding-rule", f.ruleId),
          element("span", "", f.category),
          element("span", `sev ${severity}`, f.severity === "warn" ? "Notice" : "Violation"),
        );
        const body = element("span", "finding-body");
        body.append(head, element("span", "finding-match", `“${f.match}”`), element("span", "finding-msg", f.message));
        button.append(element("span", "finding-num", String(f.id)), body);
        return button;
      })
    : problems.map((problem, i) => {
        const item = element("div", "finding is-crit");
        const body = element("span", "finding-body");
        body.append(element("span", "finding-msg", problem));
        item.append(element("span", "finding-num", String(i + 1)), body);
        return item;
      });

  findingList.replaceChildren(
    ...items.map((item) => {
      const li = document.createElement("li");
      li.append(item);
      return li;
    }),
  );
  findingsSection.hidden = items.length === 0;
}

/** Selects a finding: highlights it in the list and outlines its mark in the prompt. */
export function selectFinding(id) {
  findingList.querySelectorAll("button.finding").forEach((button) => {
    const active = button.dataset.id === String(id);
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-pressed", String(active));
    if (active) button.scrollIntoView({ block: "nearest" });
  });
  if (!isReading()) showReader(last.prompt, last.findings);
  setActiveMark(id);
}

/** Div 1 and the actions below it. */
function showStatus(isSafe, counts, hasSuggestion, marked) {
  setState("status");
  statusBox.classList.toggle("is-safe", isSafe);
  statusBox.classList.toggle("is-risk", !isSafe);

  if (isSafe) {
    statusTitle.textContent = "Approved";
    statusText.textContent = "No violations of the active rule set found.";
    subtitle.textContent = "The prompt complies with the active rule set.";
  } else {
    const parts = [];
    if (counts.crit) parts.push(plural(counts.crit, "violation", "violations"));
    if (counts.warn) parts.push(plural(counts.warn, "notice", "notices"));
    statusTitle.textContent = "Not approved";
    statusText.textContent = parts.length ? `${parts.join(" and ")} found.` : "Risk detected.";
    subtitle.textContent = marked
      ? "Marked passages violate the active rule set."
      : "The prompt violates the active rule set.";
  }
  stats.hidden = isSafe;
  statCrit.textContent = String(counts.crit);
  statWarn.textContent = String(counts.warn);
  checkState.textContent = isSafe ? "Checked: approved" : "Checked: not approved";

  sanitizeSection.hidden = false;
  safeActions.hidden = !isSafe;
  suggestionActions.hidden = isSafe || !hasSuggestion;
  noSuggestion.hidden = isSafe || hasSuggestion;
  sendNote.hidden = true;
  announce(`Result: ${statusTitle.textContent}. ${statusText.textContent}`);
}

export function showLoading() {
  fillMessages(null);
  showEditor();
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
  last = { prompt: "", findings: [] };
  showEditor();
  subtitle.textContent = SUBTITLE_INITIAL;
  setState("empty");
}

/**
 * @param {import("../shared/validate.js").CheckResponse} data
 * @param {string} prompt  the prompt that was checked (positions in data.findings refer to it)
 */
export function renderResult(data, prompt) {
  const problems = fillMessages(data);
  const findings = Array.isArray(data.findings) ? data.findings : [];
  const counts = findings.length
    ? { crit: findings.filter((f) => f.severity !== "warn").length, warn: findings.filter((f) => f.severity === "warn").length }
    : { crit: problems.length, warn: 0 };

  last = { prompt, findings };
  renderFindings(findings, problems);
  const marked = !data.isSafe && showReader(prompt, findings);
  showStatus(Boolean(data.isSafe), counts, Boolean(getSuggestion()), marked);
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

export function initResultView() {
  findingList.addEventListener("click", (event) => {
    const button = event.target.closest("button.finding");
    if (button) selectFinding(Number(button.dataset.id));
  });
}
