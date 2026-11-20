// Website entry point (Stage 1): wires up the modules and runs the check.
import { requestLlm, isMockMode } from "../shared/request_llm.js";
import { concatComplianceFiles } from "../shared/compliance_files.js";
import { $ } from "./dom.js";
import { state, canSubmit, setBusy } from "./state.js";
import { setState, showLoading, renderResult, renderError, selectFinding, initResultView } from "./result_view.js";
import { initUpload, setComplianceError, focusUpload } from "./upload.js";
import { initPromptField, getPrompt, validatePrompt, focusPrompt } from "./prompt_field.js";
import { initReader } from "./reader.js";
import { initCopy } from "./copy.js";
import { initSend } from "./send.js";
import { initSuggestion } from "./suggestion.js";

const form = $("check-form");

async function check(event) {
  event.preventDefault();
  if (!canSubmit()) return;

  const hasRules = state.complianceFiles.length > 0;
  if (!hasRules) setComplianceError("Please upload a rule set first.");
  const hasPrompt = validatePrompt();

  if (!hasRules) return focusUpload();
  if (!hasPrompt) return focusPrompt();

  const prompt = getPrompt();
  setBusy(true);
  showLoading();
  try {
    renderResult(await requestLlm(prompt, concatComplianceFiles(state.complianceFiles)), prompt);
  } catch (error) {
    renderError(error);
  } finally {
    setBusy(false);
  }
}

function init() {
  const mock = isMockMode();
  $("demo-badge").hidden = !mock;
  $("note-privacy").textContent = mock
    ? "Demo mode: your prompt does not leave the browser."
    : "Your prompt is sent to the check service before it goes anywhere else.";

  initUpload();
  initPromptField(form);
  initReader(selectFinding);
  initResultView();
  initCopy();
  initSend();
  initSuggestion();
  form.addEventListener("submit", check);
  setState("empty");
}

init();
