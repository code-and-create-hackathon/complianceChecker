// Shared page state and the Check button that depends on it.
import { $ } from "./dom.js";

/** @typedef {import("../shared/compliance_files.js").LoadedFile} LoadedFile */
/** What was checked: the trimmed prompt and the exact rules array at that time. */
/** @typedef {{prompt: string, files: LoadedFile[]}} CheckRequest */
/** @typedef {{request: CheckRequest, response: import("../shared/validate.js").CheckResponse}} CheckResult */

export const state = {
  /** @type {LoadedFile[]} Replaced (never mutated) on every change, so `!==` detects changes. */
  complianceFiles: [],
  /** @type {CheckResult | null} The result on screen, together with what it belongs to. */
  result: null,
  /** A check is running. */
  busy: false,
  /** Number of file batches still being read. */
  pendingFileReads: 0,
};

/** Checking is only possible while no check is running and no files are being read. */
export function canSubmit() {
  return !state.busy && state.pendingFileReads === 0;
}

/** True while the prompt and the rules are still exactly what `request` checked. */
export function isCurrent(request, currentPrompt) {
  return request.prompt === currentPrompt && request.files === state.complianceFiles;
}

function updateSubmitButton() {
  const submitButton = $("check-submit");
  const submitLabel = submitButton.querySelector(".button-label");
  submitButton.disabled = !canSubmit();
  submitLabel.textContent = state.busy ? "Checking …" : "Check";
}

export function setBusy(value) {
  state.busy = value;
  updateSubmitButton();
}

/** Call once when a batch of files starts loading and once when it is done (success or not). */
export function startFileRead() {
  state.pendingFileReads += 1;
  updateSubmitButton();
}

export function endFileRead() {
  state.pendingFileReads -= 1;
  updateSubmitButton();
}
