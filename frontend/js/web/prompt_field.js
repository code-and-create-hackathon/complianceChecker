// Prompt field: character count, Ctrl/⌘ + Enter, error message.
import { $, setFieldError } from "./dom.js";
import { plural } from "../shared/format.js";
import { charLength } from "../shared/validate.js";
import { resetResult } from "./result_view.js";

const promptInput = () => $("prompt");
const promptError = () => $("prompt-error");

export function getPrompt() {
  return promptInput().value.trim();
}

/** Checks that a prompt was entered; shows the error otherwise. */
export function validatePrompt() {
  const ok = Boolean(getPrompt());
  setFieldError(promptInput(), promptError(), ok ? "" : "Please enter a prompt.");
  return ok;
}

/** Replaces the prompt like a user edit would (char count, error, stale result). */
export function setPrompt(text) {
  promptInput().value = text;
  promptInput().dispatchEvent(new Event("input"));
  promptInput().focus();
}

export function focusPrompt() {
  promptInput().focus();
}

function updateCharCount() {
  // Counted like the API limit (API.md): UTF-16 code units, an emoji counts as 2.
  $("char-count").textContent = plural(charLength(promptInput().value), "character", "characters");
}

/** @param {HTMLFormElement} form */
export function initPromptField(form) {
  const isMac = /Mac|iPhone|iPad/.test(navigator.userAgent);
  $("shortcut-key").textContent = isMac ? "⌘" : "Ctrl";

  promptInput().addEventListener("input", () => {
    updateCharCount();
    resetResult();
    if (getPrompt()) setFieldError(promptInput(), promptError(), "");
  });

  promptInput().addEventListener("keydown", (event) => {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  updateCharCount();
}
