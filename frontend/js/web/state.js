// Shared page state and the Check button that depends on it.
import { $ } from "./dom.js";

/** @typedef {import("../shared/compliance_files.js").ComplianceFile & {size: number}} LoadedFile */

export const state = {
  /** @type {LoadedFile[]} */
  complianceFiles: [],
  /** A check is running. */
  busy: false,
  /** Files are still being read. */
  readingFiles: false,
};

const submitButton = $("check-submit");
const submitLabel = submitButton.querySelector(".button-label");

/** Checking is only possible while no check is running and no files are being read. */
export function canSubmit() {
  return !state.busy && !state.readingFiles;
}

function updateSubmitButton() {
  submitButton.disabled = !canSubmit();
  submitLabel.textContent = state.busy ? "Wird geprüft …" : "Prüfen";
}

export function setBusy(value) {
  state.busy = value;
  updateSubmitButton();
}

export function setReadingFiles(value) {
  state.readingFiles = value;
  updateSubmitButton();
}
