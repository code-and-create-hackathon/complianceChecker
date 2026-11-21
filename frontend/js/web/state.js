// Shared page state and the Check button that depends on it.
import { $ } from "./dom.js";

/** @typedef {import("../shared/compliance_files.js").ComplianceFile & {size: number}} LoadedFile */

export const state = {
  /** @type {LoadedFile[]} */
  complianceFiles: [],
  /** A check is running. */
  busy: false,
  /** Number of file batches still being read. */
  pendingFileReads: 0,
};

const submitButton = $("check-submit");
const submitLabel = submitButton.querySelector(".button-label");

/** Checking is only possible while no check is running and no files are being read. */
export function canSubmit() {
  return !state.busy && state.pendingFileReads === 0;
}

function updateSubmitButton() {
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
