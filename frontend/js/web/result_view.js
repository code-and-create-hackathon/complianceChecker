// Result card: states (empty, loading, status, error) and the hidden messages.
import { $, announce } from "./dom.js";
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

/** Div 2: store the messages from the API response. Always stays hidden. */
function fillMessages(data) {
  const problems = Array.isArray(data?.problems) ? data.problems : [];
  messages.dataset.isSafe = data ? String(Boolean(data.isSafe)) : "";
  messageProblems.replaceChildren(
    ...problems.map((problem) => {
      const li = document.createElement("li");
      li.textContent = `${problem.ruleId} · ${problem.category}`;
      return li;
    }),
  );
  $("result-problems-visible").replaceChildren(...problems.map((problem) => {
    const item = document.createElement("li");
    item.textContent = `${problem.ruleId} · ${problem.category}`;
    return item;
  }));
  messageImproved.textContent = (data?.improvedVersion || "").trim();
  messageRaw.textContent = data ? JSON.stringify(data, null, 2) : "";
  return problems;
}

/** Div 1: show the status. */
function showStatus(isSafe, problemCount, hasSuggestion) {
  statusBox.classList.toggle("is-safe", isSafe);
  statusBox.classList.toggle("is-risk", !isSafe);

  if (isSafe) {
    statusTitle.textContent = "Keine Verstöße gefunden";
    statusText.textContent = "Der Prompt kann gesendet werden.";
  } else {
    statusTitle.textContent =
      problemCount === 0 ? "Risiko erkannt"
      : problemCount === 1 ? "1 Problem gefunden"
      : `${problemCount} Probleme gefunden`;
    statusText.textContent = "Bitte überarbeiten Sie den Prompt vor dem Senden.";
  }

  safeActions.hidden = !isSafe;
  suggestionActions.hidden = isSafe || !hasSuggestion;
  setState("status");
  announce(`Ergebnis: ${statusTitle.textContent}.`);
}

export function showLoading() {
  fillMessages(null);
  setState("loading");
  announce("Prüfung läuft.");
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
  showStatus(Boolean(data.isSafe), problems.length, Boolean(getSuggestion()));
  document.dispatchEvent(new CustomEvent("compliance:result", { detail: data }));
}

export function renderError(error) {
  let message = error.message || "Unbekannter Fehler.";
  errorText.textContent = message;
  setState("error");
  announce(`Prüfung fehlgeschlagen. ${message}`);
}
