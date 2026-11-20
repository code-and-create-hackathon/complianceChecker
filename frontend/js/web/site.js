// Website entry point (Stage 1).
import { requestLlm, isMockMode } from "../shared/request_llm.js";

const MAX_FILE_BYTES = 1024 * 1024;
const ACCEPTED_FILE = /\.(md|markdown|txt)$/i;
const COPY_FEEDBACK_MS = 1600;

const byId = (id) => document.getElementById(id);

const form = byId("check-form");
const promptInput = form.elements.prompt;
const fileInput = form.elements.compliance;

const dropzone = byId("dropzone");
const fileChip = byId("file-chip");
const fileName = byId("file-name");
const fileMeta = byId("file-meta");
const fileRemove = byId("file-remove");
const complianceError = byId("compliance-error");
const promptError = byId("prompt-error");
const charCount = byId("char-count");
const submitButton = byId("submit-button");
const submitLabel = submitButton.querySelector(".button-label");

const result = byId("result");
const states = result.querySelectorAll("[data-state]");
const srStatus = byId("sr-status");
const errorText = byId("error-text");

// Div 1: Status (sichtbar)
const statusBox = byId("result-status");
const statusTitle = byId("status-title");
const statusText = byId("status-text");
const safeActions = byId("safe-actions");

// Div 2: Meldungen (versteckt, für Entwickler)
const messages = byId("result-messages");
const messageProblems = byId("message-problems");
const messageImproved = byId("message-improved");
const messageRaw = byId("message-raw");

const numberFormat = new Intl.NumberFormat("de-DE");

let complianceText = "";
let busy = false;

// ---------- Ergebnis ----------

function setState(name) {
  states.forEach((state) => {
    state.hidden = state.dataset.state !== name;
  });
  result.setAttribute("aria-busy", String(name === "loading"));
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

function clearFile() {
  complianceText = "";
  fileInput.value = "";
  fileChip.hidden = true;
  dropzone.hidden = false;
}

async function loadFile(file) {
  if (!file) return;

  if (!ACCEPTED_FILE.test(file.name)) {
    clearFile();
    setComplianceError("Bitte eine Markdown- oder Textdatei (.md, .txt) wählen.");
    return;
  }
  if (file.size > MAX_FILE_BYTES) {
    clearFile();
    setComplianceError("Die Datei ist größer als 1 MB.");
    return;
  }

  const text = await file.text();
  if (!text.trim()) {
    clearFile();
    setComplianceError("Die Datei ist leer.");
    return;
  }

  complianceText = text;
  const rules = countRules(text);
  fileName.textContent = file.name;
  fileMeta.textContent =
    `${formatSize(file.size)} · ` +
    (rules > 0 ? `${numberFormat.format(rules)} ${rules === 1 ? "Regel" : "Regeln"}` : "Regelwerk geladen");

  setComplianceError("");
  dropzone.hidden = true;
  fileChip.hidden = false;
}

fileInput.addEventListener("change", () => loadFile(fileInput.files[0]));

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
  loadFile(event.dataTransfer.files[0]);
});

// Verhindert, dass der Browser eine danebengezogene Datei öffnet.
["dragover", "drop"].forEach((type) => {
  window.addEventListener(type, (event) => event.preventDefault());
});

fileRemove.addEventListener("click", () => {
  clearFile();
  fileInput.focus();
});

// ---------- Prompt ----------

function updateCharCount() {
  charCount.textContent = `${numberFormat.format(promptInput.value.length)} Zeichen`;
}

promptInput.addEventListener("input", () => {
  updateCharCount();
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
  submitButton.disabled = value;
  submitLabel.textContent = value ? "Wird geprüft …" : "Prüfen";
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (busy) return;

  const prompt = promptInput.value.trim();
  setComplianceError(complianceText ? "" : "Bitte laden Sie Ihre Compliance-Regeln hoch.");
  setFieldError(promptInput, promptError, prompt ? "" : "Bitte geben Sie einen Prompt ein.");

  if (!complianceText) return fileInput.focus();
  if (!prompt) return promptInput.focus();

  setBusy(true);
  fillMessages(null);
  setState("loading");
  announce("Prüfung läuft.");

  try {
    renderResult(await requestLlm(prompt, complianceText));
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
