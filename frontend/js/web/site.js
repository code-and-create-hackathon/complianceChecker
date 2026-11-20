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
const emptyTitle = byId("empty-title");
const emptyText = byId("empty-text");
const riskTitle = byId("risk-title");
const problemSection = byId("problem-section");
const problemList = byId("problem-list");
const improvedBlock = byId("improved-block");
const improvedText = byId("improved-text");
const applyButton = byId("apply-button");
const errorText = byId("error-text");

const numberFormat = new Intl.NumberFormat("de-DE");

let complianceText = "";
let busy = false;

// ---------- Ergebnis-Zustände ----------

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

function showEmpty(title, text) {
  emptyTitle.textContent = title;
  emptyText.textContent = text;
  setState("empty");
}

function renderResult({ isSafe, problems, improvedVersion }) {
  if (isSafe) {
    setState("safe");
    announce("Ergebnis: Keine Verstöße gefunden.");
    return;
  }

  const items = Array.isArray(problems) ? problems.filter(Boolean) : [];
  problemList.replaceChildren(
    ...items.map((problem) => {
      const li = document.createElement("li");
      li.textContent = problem;
      return li;
    }),
  );
  problemSection.hidden = items.length === 0;
  riskTitle.textContent =
    items.length === 0 ? "Risiko erkannt"
    : items.length === 1 ? "1 Problem gefunden"
    : `${items.length} Probleme gefunden`;

  const improved = (improvedVersion || "").trim();
  improvedText.textContent = improved;
  improvedBlock.hidden = !improved;

  setState("risk");
  announce(`Ergebnis: ${riskTitle.textContent}.`);
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
    (rules > 0 ? `${numberFormat.format(rules)} ${rules === 1 ? "Regel" : "Regeln"} erkannt` : "Regelwerk geladen");

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
  submitLabel.textContent = value ? "Wird geprüft …" : "Prompt prüfen";
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

// ---------- Kopieren & Übernehmen ----------

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

  const text = button.dataset.copy === "improved" ? improvedText.textContent : promptInput.value;
  const label = button.querySelector(".button-label");
  label.dataset.original ??= label.textContent;

  const ok = await copyText(text);
  label.textContent = ok ? "Kopiert" : "Kopieren fehlgeschlagen";
  announce(label.textContent);
  setTimeout(() => {
    label.textContent = label.dataset.original;
  }, COPY_FEEDBACK_MS);
});

applyButton.addEventListener("click", () => {
  promptInput.value = improvedText.textContent;
  updateCharCount();
  setFieldError(promptInput, promptError, "");
  showEmpty(
    "Verbesserte Version übernommen",
    "Prüfen Sie den angepassten Prompt erneut, bevor Sie ihn senden.",
  );
  announce("Verbesserte Version übernommen. Bitte erneut prüfen.");
  promptInput.focus();
});

// ---------- Start ----------

function init() {
  const isMac = /Mac|iPhone|iPad/.test(navigator.userAgent);
  byId("shortcut-key").textContent = isMac ? "⌘" : "Strg";

  if (isMockMode()) {
    byId("demo-badge").hidden = false;
    byId("form-note").hidden = false;
  }

  updateCharCount();
  setState("empty");
}

init();
