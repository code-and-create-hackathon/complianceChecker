// Prompt field: character count, Ctrl/⌘ + Enter, error message.
import { $, numberFormat, setFieldError } from "./dom.js";
import { resetResult } from "./result_view.js";

const promptInput = $("prompt");
const promptError = $("prompt-error");
const charCount = $("char-count");

export function getPrompt() {
  return promptInput.value.trim();
}

/** Checks that a prompt was entered; shows the error otherwise. */
export function validatePrompt() {
  const ok = Boolean(getPrompt());
  setFieldError(promptInput, promptError, ok ? "" : "Please enter a prompt.");
  return ok;
}

/** Replaces the prompt like a user edit would (char count, error, stale result). */
export function setPrompt(text) {
  promptInput.value = text;
  promptInput.dispatchEvent(new Event("input"));
  promptInput.focus();
}

export function focusPrompt() {
  promptInput.focus();
}

function updateCharCount() {
  // Characters = Unicode code points as in API.md (an emoji counts as 1).
  const count = [...promptInput.value].length;
  charCount.textContent = `${numberFormat.format(count)} ${count === 1 ? "character" : "characters"}`;
}

/** @param {HTMLFormElement} form */
export function initPromptField(form) {
  const isMac = /Mac|iPhone|iPad/.test(navigator.userAgent);
  $("shortcut-key").textContent = isMac ? "⌘" : "Ctrl";

  promptInput.addEventListener("input", () => {
    updateCharCount();
    resetResult();
    if (getPrompt()) setFieldError(promptInput, promptError, "");
  });

  promptInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  updateCharCount();
}
