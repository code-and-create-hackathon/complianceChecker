// Compliance rules upload: dropzone, file chips, file checks.
import { loadComplianceFiles, addWithinLimit, skippedMessage, ruleSummary } from "../shared/compliance_files.js";
import { formatSize, plural } from "../shared/format.js";
import { $, setFieldError } from "./dom.js";
import { state, startFileRead, endFileRead } from "./state.js";
import { resetResult } from "./result_view.js";

const fileInput = $("compliance");
const dropzone = $("dropzone");
const fileSummary = $("compliance-summary");
const fileStatus = $("compliance-status");
const fileList = $("compliance-files");
const fileClear = $("compliance-clear");
const chipTemplate = $("file-chip-template");
const complianceError = $("compliance-error");

/**
 * Bumped whenever the loaded rules are thrown away (clear all, read error).
 * A batch that started before that discards its result instead of adding to the new state.
 */
let loadGeneration = 0;

/** Called after every change of the loaded files (set in initUpload). */
let onFilesChanged = () => {};

// ---------- Messages ----------

export function setComplianceError(message) {
  setFieldError(fileInput, complianceError, message);
  dropzone.classList.toggle("is-invalid", Boolean(message));
}

/** Notice below the upload without marking the field invalid (files are still loaded). */
function setComplianceNotice(message) {
  setComplianceError("");
  complianceError.textContent = message;
  complianceError.hidden = !message;
}

export function focusUpload() {
  fileInput.focus();
}

// ---------- Rendering ----------

function renderFiles() {
  const files = state.complianceFiles;
  fileList.replaceChildren(
    ...files.map((file, index) => {
      const chip = chipTemplate.content.firstElementChild.cloneNode(true);
      chip.querySelector(".file-name").textContent = file.name;
      chip.querySelector(".file-meta").textContent = `${formatSize(file.size)} · ${ruleSummary(file.text)}`;
      const remove = chip.querySelector(".file-remove");
      remove.dataset.index = String(index);
      remove.setAttribute("aria-label", `Remove ${file.name}`);
      return chip;
    }),
  );
  fileStatus.textContent =
    `${plural(files.length, "file", "files")} loaded`;
  fileSummary.hidden = files.length === 0;
}

/** After every change to the rules: re-render and discard the old result. */
function setFiles(files) {
  state.complianceFiles = files;
  renderFiles();
  resetResult();
  onFilesChanged();
}

/** Removes all loaded files, also those still being read. */
export function clearFiles() {
  loadGeneration += 1; // files still loading must not reappear
  setComplianceError("");
  setFiles([]);
}

// ---------- Loading ----------

/**
 * Adds the files to the loaded ones (same name replaces the old file).
 * Wrong types, empty files and files that would exceed the total limit are discarded and reported.
 */
async function loadFiles(fileListLike) {
  const picked = [...(fileListLike || [])];
  fileInput.value = "";
  if (!picked.length) return;

  const generation = loadGeneration;
  startFileRead();
  resetResult();
  try {
    const { loaded, skipped } = await loadComplianceFiles(picked);
    if (generation !== loadGeneration) return; // rules were cleared or failed meanwhile
    // Merge with the state as it is now (other batches may have finished meanwhile).
    const { files: merged, skipped: tooLong } = addWithinLimit(state.complianceFiles, loaded);
    setFiles(merged);

    const message = skippedMessage([...skipped, ...tooLong]);
    if (state.complianceFiles.length) setComplianceNotice(message);
    else setComplianceError(message || "No valid file loaded.");
  } catch (error) {
    // Fail loudly: never keep using half-loaded or outdated rules, also not from other pending batches.
    loadGeneration += 1;
    setFiles([]);
    setComplianceError(
      `File could not be read (${error.message || error}). Please upload all rules again.`,
    );
  } finally {
    endFileRead();
  }
}

// ---------- Events ----------

/** @param {{onFilesChanged?: () => void}} [options] */
export function initUpload({ onFilesChanged: callback } = {}) {
  if (callback) onFilesChanged = callback;
  fileInput.addEventListener("change", () => loadFiles(fileInput.files));

  dropzone.addEventListener("click", (event) => {
    if (event.target !== fileInput) fileInput.click();
  });

  dropzone.addEventListener("dragover", (event) => {
    event.preventDefault();
    dropzone.classList.add("is-dragover");
  });

  dropzone.addEventListener("dragleave", (event) => {
    if (!dropzone.contains(event.relatedTarget)) dropzone.classList.remove("is-dragover");
  });

  dropzone.addEventListener("drop", (event) => {
    event.preventDefault();
    dropzone.classList.remove("is-dragover");
    loadFiles(event.dataTransfer.files);
  });

  // Stops the browser from opening a file dropped outside the dropzone.
  ["dragover", "drop"].forEach((type) => {
    window.addEventListener(type, (event) => event.preventDefault());
  });

  fileList.addEventListener("click", (event) => {
    const button = event.target.closest(".file-remove");
    if (!button) return;
    const index = Number(button.dataset.index);
    setComplianceError("");
    setFiles(state.complianceFiles.filter((_, i) => i !== index));
    fileInput.focus();
  });

  fileClear.addEventListener("click", () => {
    clearFiles();
    fileInput.focus();
  });

  renderFiles();
}
