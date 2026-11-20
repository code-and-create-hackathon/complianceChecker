// Website entry point (Stage 1).
import { requestLlm, isMockMode } from "../shared/request_llm.js";
import { readComplianceFiles, concatComplianceFiles } from "../shared/compliance_files.js";

const MAX_FILE_BYTES = 1024 * 1024;
const ACCEPTED_FILE = /\.(md|markdown|txt)$/i;
const COPY_FEEDBACK_MS = 1600;

const byId = (id) => document.getElementById(id);

const form = byId("check-form");
const promptInput = form.elements.prompt;
const fileInput = form.elements.compliance;

const dropzone = byId("dropzone");
const fileSummary = byId("compliance-summary");
const fileStatus = byId("compliance-status");
const fileList = byId("compliance-files");
const fileClear = byId("compliance-clear");
const chipTemplate = byId("file-chip-template");
const complianceError = byId("compliance-error");
const promptError = byId("prompt-error");
const charCount = byId("char-count");
const submitButton = byId("check-submit");
const submitLabel = submitButton.querySelector(".button-label");

const result = byId("result");
const states = result.querySelectorAll("[data-state]");
const srStatus = byId("sr-status");
const errorText = byId("error");

// Div 1: Status (sichtbar)
const statusBox = byId("result-status");
const statusTitle = byId("status-title");
const statusText = byId("status-text");
const safeActions = byId("safe-actions");

// Div 2: Meldungen (versteckt, für Entwickler)
const messages = byId("result-messages");
const messageProblems = byId("result-problems");
const messageImproved = byId("result-improved-text");
const messageRaw = byId("message-raw");

const numberFormat = new Intl.NumberFormat("de-DE");

/** @type {(import("../shared/compliance_files.js").ComplianceFile & {size: number})[]} */
let complianceFiles = [];
let busy = false;
let readingFiles = false;

// ---------- Ergebnis ----------

function setState(name) {
  states.forEach((state) => {
    state.hidden = state.dataset.state !== name;
  });
  result.setAttribute("aria-busy", String(name === "loading"));
}

/** Verwirft das letzte Ergebnis, sobald sich Regeln oder Prompt ändern. */
function resetResult() {
  if (busy) return;
  fillMessages(null);
  setState("empty");
}

function announce(message) {
  srStatus.textContent = "";
  requestAnimationFrame(() => {
    srStatus.textContent = message;
  });
}

/** Div 2: Meldungen aus der API-Antwort ablegen. Bleibt immer versteckt. */
function fillMessages(data) {
  const problems = Array.isArray(data?.problems) ? data.problems.filter(Boolean) : [];
  messages.dataset.isSafe = data ? String(Boolean(data.isSafe)) : "";
  messageProblems.replaceChildren(
    ...problems.map((problem) => {
      const li = document.createElement("li");
      li.textContent = problem;
      return li;
    }),
  );
  messageImproved.textContent = (data?.improvedVersion || "").trim();
  messageRaw.textContent = data ? JSON.stringify(data, null, 2) : "";
  return problems;
}

/** Div 1: Status anzeigen. */
function showStatus(isSafe, problemCount) {
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
  setState("status");
  announce(`Ergebnis: ${statusTitle.textContent}.`);
}

function renderResult(data) {
  const problems = fillMessages(data);
  showStatus(Boolean(data.isSafe), problems.length);
  document.dispatchEvent(new CustomEvent("compliance:result", { detail: data }));
}

function renderError(error) {
  let message = error.message || "Unbekannter Fehler.";
  if (!isMockMode()) {
    message += " Ohne Backend die Seite mit ?mock=1 öffnen, um den Demo-Modus zu nutzen.";
  }
  errorText.textContent = message;
  setState("error");
  announce(`Prüfung fehlgeschlagen. ${message}`);
}

// ---------- Validierung ----------

function setFieldError(input, errorEl, message) {
  errorEl.textContent = message;
  errorEl.hidden = !message;
  if (message) input.setAttribute("aria-invalid", "true");
  else input.removeAttribute("aria-invalid");
}

function setComplianceError(message) {
  setFieldError(fileInput, complianceError, message);
  dropzone.classList.toggle("is-invalid", Boolean(message));
}

/** Hinweis unter dem Upload, ohne das Feld als ungültig zu markieren (es sind noch Dateien geladen). */
function setComplianceNotice(message) {
  setComplianceError("");
  complianceError.textContent = message;
  complianceError.hidden = !message;
}

// ---------- Datei-Upload ----------

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toLocaleString("de-DE", { maximumFractionDigits: 1 })} KB`;
}

function countRules(markdown) {
  return markdown
    .split(/\r?\n/)
    .filter((line) => /^\s*(?:[-*+]|\d+[.)])\s+\S/.test(line)).length;
}

function ruleSummary(text) {
  const rules = countRules(text);
  return rules > 0 ? `${numberFormat.format(rules)} ${rules === 1 ? "Regel" : "Regeln"}` : "Regelwerk geladen";
}

function renderFiles() {
  fileList.replaceChildren(
    ...complianceFiles.map((file, index) => {
      const chip = chipTemplate.content.firstElementChild.cloneNode(true);
      chip.querySelector(".file-name").textContent = file.name;
      chip.querySelector(".file-meta").textContent = `${formatSize(file.size)} · ${ruleSummary(file.text)}`;
      const remove = chip.querySelector(".file-remove");
      remove.dataset.index = String(index);
      remove.setAttribute("aria-label", `${file.name} entfernen`);
      return chip;
    }),
  );
  const count = complianceFiles.length;
  fileStatus.textContent = count === 1 ? "1 Datei geladen" : `${numberFormat.format(count)} Dateien geladen`;
  fileSummary.hidden = count === 0;
}

function clearFiles() {
  complianceFiles = [];
  fileInput.value = "";
  setComplianceError("");
  renderFiles();
  resetResult();
}

function setReadingFiles(value) {
  readingFiles = value;
  submitButton.disabled = busy || readingFiles;
}

/**
 * Fügt die Dateien zu den geladenen hinzu (gleicher Name ersetzt die alte Datei).
 * Falsche Typen, zu große und leere Dateien werden verworfen und gemeldet.
 */
async function loadFiles(fileListLike) {
  const picked = [...(fileListLike || [])];
  fileInput.value = "";
  if (!picked.length) return;

  const skipped = [];
  const candidates = picked.filter((file) => {
    if (!ACCEPTED_FILE.test(file.name)) skipped.push(`${file.name} (kein .md/.txt)`);
    else if (file.size > MAX_FILE_BYTES) skipped.push(`${file.name} (größer als 1 MB)`);
    else return true;
    return false;
  });

  setReadingFiles(true);
  resetResult();
  try {
    const { files, emptyNames } = await readComplianceFiles(candidates);
    emptyNames.forEach((name) => skipped.push(`${name} (leer)`));
    const sizes = new Map(candidates.map((file) => [file.name, file.size]));
    const loaded = files.map((file) => ({ ...file, size: sizes.get(file.name) ?? 0 }));
    const newNames = new Set(loaded.map((file) => file.name));
    complianceFiles = [...complianceFiles.filter((file) => !newNames.has(file.name)), ...loaded];
    renderFiles();

    const message = skipped.length ? `Nicht übernommen: ${skipped.join(", ")}.` : "";
    if (complianceFiles.length) setComplianceNotice(message);
    else setComplianceError(message || "Keine gültige Datei geladen.");
  } catch (error) {
    // Laut scheitern: keine halb geladenen oder veralteten Regeln weiterverwenden.
    complianceFiles = [];
    renderFiles();
    setComplianceError(
      `Datei konnte nicht gelesen werden (${error.message || error}). Bitte alle Regeln erneut hochladen.`,
    );
  } finally {
    setReadingFiles(false);
  }
}

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

// Verhindert, dass der Browser eine danebengezogene Datei öffnet.
["dragover", "drop"].forEach((type) => {
  window.addEventListener(type, (event) => event.preventDefault());
});

fileList.addEventListener("click", (event) => {
  const button = event.target.closest(".file-remove");
  if (!button) return;
  complianceFiles.splice(Number(button.dataset.index), 1);
  setComplianceError("");
  renderFiles();
  resetResult();
  fileInput.focus();
});

fileClear.addEventListener("click", () => {
  clearFiles();
  fileInput.focus();
});

// ---------- Prompt ----------

function updateCharCount() {
  // Zeichen = Unicode-Codepoints wie in API.md (ein Emoji zählt als 1).
  charCount.textContent = `${numberFormat.format([...promptInput.value].length)} Zeichen`;
}

promptInput.addEventListener("input", () => {
  updateCharCount();
  resetResult();
  if (promptInput.value.trim()) setFieldError(promptInput, promptError, "");
});

promptInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
    event.preventDefault();
    form.requestSubmit();
  }
});

// ---------- Prüfen ----------

function setBusy(value) {
  busy = value;
  submitButton.disabled = value || readingFiles;
  submitLabel.textContent = value ? "Wird geprüft …" : "Prüfen";
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (busy || readingFiles) return;

  const prompt = promptInput.value.trim();
  const hasRules = complianceFiles.length > 0;
  if (!hasRules) setComplianceError("Bitte laden Sie Ihre Compliance-Regeln hoch.");
  setFieldError(promptInput, promptError, prompt ? "" : "Bitte geben Sie einen Prompt ein.");

  if (!hasRules) return fileInput.focus();
  if (!prompt) return promptInput.focus();

  setBusy(true);
  fillMessages(null);
  setState("loading");
  announce("Prüfung läuft.");

  try {
    renderResult(await requestLlm(prompt, concatComplianceFiles(complianceFiles)));
  } catch (error) {
    renderError(error);
  } finally {
    setBusy(false);
  }
});

// ---------- Kopieren ----------

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    const helper = document.createElement("textarea");
    helper.value = text;
    helper.setAttribute("readonly", "");
    helper.style.position = "fixed";
    helper.style.opacity = "0";
    document.body.append(helper);
    helper.select();
    const ok = document.execCommand("copy");
    helper.remove();
    return ok;
  }
}

result.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-copy]");
  if (!button) return;

  const label = button.querySelector(".button-label");
  label.dataset.original ??= label.textContent;

  const ok = await copyText(promptInput.value);
  label.textContent = ok ? "Kopiert" : "Kopieren fehlgeschlagen";
  announce(label.textContent);
  setTimeout(() => {
    label.textContent = label.dataset.original;
  }, COPY_FEEDBACK_MS);
});

// ---------- Start ----------

function init() {
  const isMac = /Mac|iPhone|iPad/.test(navigator.userAgent);
  byId("shortcut-key").textContent = isMac ? "⌘" : "Strg";
  byId("demo-badge").hidden = !isMockMode();
  updateCharCount();
  setState("empty");
}

init();
