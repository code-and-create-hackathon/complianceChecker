// Compliance rules upload: dropzone, file chips, file checks.
import { readComplianceFiles, concatComplianceFiles } from "../shared/compliance_files.js";
import { COMPLIANCE_MAX_LENGTH, charLength } from "../shared/validate.js";
import { $, LOCALE, numberFormat, plural, setFieldError } from "./dom.js";
import { state, startFileRead, endFileRead } from "./state.js";
import { resetResult } from "./result_view.js";

const ACCEPTED_FILE = /\.(md|markdown|txt)$/i;

const fileInput = $("compliance");
const dropzone = $("dropzone");
const fileSummary = $("compliance-summary");
const fileStatus = $("compliance-status");
const fileList = $("compliance-files");
const fileClear = $("compliance-clear");
const chipTemplate = $("file-chip-template");
const complianceError = $("compliance-error");

const TOO_LONG = `rules exceed ${numberFormat.format(COMPLIANCE_MAX_LENGTH)} characters in total`;

/**
 * Bumped whenever the loaded rules are thrown away (clear all, read error).
 * A batch that started before that discards its result instead of adding to the new state.
 */
let loadGeneration = 0;

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

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toLocaleString(LOCALE, { maximumFractionDigits: 1 })} KB`;
}

function countRules(markdown) {
  return markdown
    .split(/\r?\n/)
    .filter((line) => /^\s*(?:[-*+]|\d+[.)])\s+\S/.test(line)).length;
}

function ruleSummary(text) {
  const rules = countRules(text);
  return rules > 0 ? plural(rules, "rule", "rules") : "rule set loaded";
}

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
}

// ---------- Loading ----------

/**
 * Adds files to `current` in order (same name replaces the old file) as long as all rules
 * together stay within the API limit, measured exactly like validateRequest does.
 * @returns {{files: import("./state.js").LoadedFile[], tooLong: string[]}}
 */
function addWithinLimit(current, loaded) {
  let files = current;
  const tooLong = [];
  for (const file of loaded) {
    const next = [...files.filter((f) => f.name !== file.name), file];
    if (charLength(concatComplianceFiles(next)) > COMPLIANCE_MAX_LENGTH) tooLong.push(file.name);
    else files = next;
  }
  return { files, tooLong };
}

/**
 * Adds the files to the loaded ones (same name replaces the old file).
 * Wrong types, empty files and files that would exceed the total limit are discarded and reported.
 */
async function loadFiles(fileListLike) {
  const picked = [...(fileListLike || [])];
  fileInput.value = "";
  if (!picked.length) return;

  const skipped = [];
  const candidates = picked.filter((file) => {
    if (!ACCEPTED_FILE.test(file.name)) skipped.push(`${file.name} (not .md/.txt)`);
    // UTF-8 needs at most 4 bytes per character: anything bigger is too long for sure, skip reading it.
    else if (file.size > COMPLIANCE_MAX_LENGTH * 4) skipped.push(`${file.name} (${TOO_LONG})`);
    else return true;
    return false;
  });

  const generation = loadGeneration;
  startFileRead();
  resetResult();
  try {
    const { files, emptyNames } = await readComplianceFiles(candidates);
    if (generation !== loadGeneration) return; // rules were cleared or failed meanwhile
    emptyNames.forEach((name) => skipped.push(`${name} (empty)`));
    const sizes = new Map(candidates.map((file) => [file.name, file.size]));
    const loaded = files.map((file) => ({ ...file, size: sizes.get(file.name) ?? 0 }));
    const { files: merged, tooLong } = addWithinLimit(state.complianceFiles, loaded);
    tooLong.forEach((name) => skipped.push(`${name} (${TOO_LONG})`));
    setFiles(merged);

    const message = skipped.length ? `Not added: ${skipped.join(", ")}.` : "";
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

export function initUpload() {
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
    loadGeneration += 1; // files still loading must not reappear after "Remove all"
    setComplianceError("");
    setFiles([]);
    fileInput.focus();
  });

  renderFiles();
}
