// Compliance rules upload: rule-set button and menu in the header, drag & drop anywhere on the page,
// file list, file checks.
import { readComplianceFiles, concatComplianceFiles } from "../shared/compliance_files.js";
import { COMPLIANCE_MAX_LENGTH, charLength } from "../shared/validate.js";
import { $, LOCALE, numberFormat, plural, setFieldError } from "./dom.js";
import { state, startFileRead, endFileRead } from "./state.js";
import { resetResult } from "./result_view.js";

const ACCEPTED_FILE = /\.(md|markdown|txt)$/i;

const fileInput = $("compliance");
const rulesetButton = $("ruleset-button");
const rulesetName = $("ruleset-name");
const rulesetMeta = $("ruleset-meta");
const menu = $("ruleset-menu");
const uploadItem = $("ruleset-upload");
const fileSummary = $("compliance-summary");
const fileStatus = $("compliance-status");
const fileList = $("compliance-files");
const fileClear = $("compliance-clear");
const chipTemplate = $("file-chip-template");
const complianceError = $("compliance-error");
const rulesetNote = $("note-ruleset");
const dropOverlay = $("drop-overlay");

const timeFormat = new Intl.DateTimeFormat(LOCALE, { hour: "2-digit", minute: "2-digit" });

const TOO_LONG = `rules exceed ${numberFormat.format(COMPLIANCE_MAX_LENGTH)} characters in total`;

/**
 * Bumped whenever the loaded rules are thrown away (clear all, read error).
 * A batch that started before that discards its result instead of adding to the new state.
 */
let loadGeneration = 0;

// ---------- Messages ----------

export function setComplianceError(message) {
  setFieldError(fileInput, complianceError, message);
  rulesetButton.classList.toggle("is-invalid", Boolean(message));
}

/** Notice below the editor without marking the rules invalid (files are still loaded). */
function setComplianceNotice(message) {
  setComplianceError("");
  complianceError.textContent = message;
  complianceError.hidden = !message;
}

export function focusUpload() {
  rulesetButton.focus();
}

// ---------- Menu ----------

function openMenu() {
  menu.hidden = false;
  rulesetButton.setAttribute("aria-expanded", "true");
  uploadItem.focus();
}

function closeMenu({ focusButton = false } = {}) {
  if (menu.hidden) return;
  menu.hidden = true;
  rulesetButton.setAttribute("aria-expanded", "false");
  if (focusButton) rulesetButton.focus();
}

function pickFiles() {
  closeMenu();
  fileInput.click();
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

/** Header button: "Upload rule set" or the active rule set with rule count and load time. */
function renderButton() {
  const files = state.complianceFiles;
  const empty = files.length === 0;
  rulesetButton.classList.toggle("is-empty", empty);
  rulesetMeta.hidden = empty;

  if (empty) {
    rulesetName.textContent = "Upload rule set";
    rulesetNote.textContent = "No rule set loaded.";
    return;
  }

  const rules = files.reduce((sum, file) => sum + countRules(file.text), 0);
  const time = timeFormat.format(state.rulesLoadedAt);
  rulesetName.textContent = files.length === 1 ? files[0].name.replace(/\.[^.]+$/, "") : plural(files.length, "rule file", "rule files");
  rulesetMeta.textContent = rules > 0 ? `${plural(rules, "rule", "rules")}, loaded at ${time}` : `loaded at ${time}`;
  rulesetNote.textContent = `Active rule set: ${files.map((f) => f.name).join(", ")} · loaded at ${time}.`;
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
  fileStatus.textContent = `${plural(files.length, "file", "files")} loaded`;
  fileSummary.hidden = files.length === 0;
  renderButton();
}

/** After every change to the rules: re-render and discard the old result. */
function setFiles(files) {
  state.complianceFiles = files;
  state.rulesLoadedAt = files.length ? new Date() : null;
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
    setComplianceError(`File could not be read (${error.message || error}). Please upload all rules again.`);
  } finally {
    endFileRead();
  }
}

// ---------- Drag & drop anywhere on the page ----------

function initDragAndDrop() {
  let depth = 0;
  const hasFiles = (event) => [...(event.dataTransfer?.types || [])].includes("Files");

  window.addEventListener("dragenter", (event) => {
    if (!hasFiles(event)) return;
    depth += 1;
    dropOverlay.hidden = false;
  });

  window.addEventListener("dragleave", (event) => {
    if (!hasFiles(event)) return;
    depth = Math.max(0, depth - 1);
    if (depth === 0) dropOverlay.hidden = true;
  });

  // Without preventDefault the browser would open the dropped file.
  window.addEventListener("dragover", (event) => event.preventDefault());

  window.addEventListener("drop", (event) => {
    event.preventDefault();
    depth = 0;
    dropOverlay.hidden = true;
    if (hasFiles(event)) loadFiles(event.dataTransfer.files);
  });
}

// ---------- Events ----------

export function initUpload() {
  fileInput.addEventListener("change", () => loadFiles(fileInput.files));

  // No rules yet: the button opens the file dialog directly; otherwise the menu.
  rulesetButton.addEventListener("click", () => {
    if (!state.complianceFiles.length) pickFiles();
    else if (menu.hidden) openMenu();
    else closeMenu();
  });

  uploadItem.addEventListener("click", pickFiles);

  document.addEventListener("click", (event) => {
    if (!event.target.closest(".ruleset")) closeMenu();
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeMenu({ focusButton: true });
  });

  fileList.addEventListener("click", (event) => {
    const button = event.target.closest(".file-remove");
    if (!button) return;
    const index = Number(button.dataset.index);
    setComplianceError("");
    setFiles(state.complianceFiles.filter((_, i) => i !== index));
    if (state.complianceFiles.length) uploadItem.focus();
    else closeMenu({ focusButton: true });
  });

  fileClear.addEventListener("click", () => {
    loadGeneration += 1; // files still loading must not reappear after "Remove all"
    setComplianceError("");
    setFiles([]);
    closeMenu({ focusButton: true });
  });

  initDragAndDrop();
  renderFiles();
}
