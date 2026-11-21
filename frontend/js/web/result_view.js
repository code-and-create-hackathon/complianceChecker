// Result panel: verdict (empty, loading, status, error), findings list, sanitise & send,
// plus the hidden developer messages.
import { isMockMode } from "../shared/request_llm.js";
import { isBlocking, NOTICE_SEVERITY } from "../shared/validate.js";
import { $, announce, plural } from "./dom.js";
import { state } from "./state.js";
import { locateProblems } from "./locate.js";
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

const SEVERITY_LABELS = { critical: "Critical", high: "High", medium: "Medium", low: "Low" };

/** Prompt and located problems of the last result, for the read view. */
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
  const problems = Array.isArray(data?.problems) ? data.problems : [];
  messages.dataset.isSafe = data ? String(Boolean(data.isSafe)) : "";
  messageProblems.replaceChildren(
    ...problems.map((p) => {
      const li = document.createElement("li");
      li.textContent = [p.ruleId, p.severity, p.message, p.location && `"${p.location}"`].filter(Boolean).join(" · ");
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

/**
 * One list entry per problem, numbered like the marks in the prompt.
 * Problems that are marked in the prompt are buttons (select → outline the mark); the others are plain.
 * @param {import("./locate.js").LocatedProblem[]} findings
 */
function renderFindings(findings) {
  const items = findings.map((f) => {
    const severity = f.severity === NOTICE_SEVERITY ? "is-warn" : "is-crit";
    const marked = f.start !== null;
    const item = element(marked ? "button" : "div", `finding ${severity}`);
    if (marked) {
      item.type = "button";
      item.dataset.id = String(f.id);
      item.setAttribute("aria-pressed", "false");
    }

    const head = element("span", "finding-head");
    head.append(
      element("span", "finding-rule", f.ruleId),
      element("span", `sev ${severity}`, SEVERITY_LABELS[f.severity] ?? f.severity),
    );
    const body = element("span", "finding-body");
    body.append(head);
    if (f.location) body.append(element("span", "finding-match", `“${f.location}”`));
    if (f.location && !marked) body.append(element("span", "finding-msg", "Passage not found in the prompt."));
    body.append(element("span", "finding-msg", f.message));
    item.append(element("span", "finding-num", String(f.id)), body);
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

  const parts = [];
  if (counts.crit) parts.push(plural(counts.crit, "violation", "violations"));
  if (counts.warn) parts.push(plural(counts.warn, "notice", "notices"));

  if (isSafe) {
    statusTitle.textContent = "Approved";
    statusText.textContent = counts.warn
      ? `No violations of the active rule set, ${parts.join("")} found.`
      : "No violations of the active rule set found.";
    subtitle.textContent = marked
      ? "Marked passages are notices only. The prompt may be sent."
      : "The prompt complies with the active rule set.";
  } else {
    statusTitle.textContent = "Not approved";
    statusText.textContent = parts.length ? `${parts.join(" and ")} found.` : "Risk detected.";
    subtitle.textContent = marked
      ? "Marked passages violate the active rule set."
      : "The prompt violates the active rule set.";
  }
  stats.hidden = counts.crit + counts.warn === 0;
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
 * @param {string} prompt  the prompt that was checked (the problems' locations are searched in it)
 */
export function renderResult(data, prompt) {
  const problems = fillMessages(data);
  const findings = locateProblems(prompt, problems);
  const blocking = problems.filter(isBlocking).length;
  const counts = { crit: blocking, warn: problems.length - blocking };

  last = { prompt, findings };
  renderFindings(findings);
  const marked = showReader(prompt, findings);
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
