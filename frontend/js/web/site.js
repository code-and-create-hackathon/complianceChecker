// Website entry point (Stage 1): wires up the modules and runs the check.
import { requestLlm, isMockMode } from "../shared/request_llm.js";
import { concatComplianceFiles } from "../shared/compliance_files.js";
import { $ } from "./dom.js";
import { state, canSubmit, setBusy } from "./state.js";
import { setState, showLoading, renderResult, renderError } from "./result_view.js";
import { initUpload, setComplianceError, focusUpload } from "./upload.js";
import { initPromptField, getPrompt, validatePrompt, focusPrompt } from "./prompt_field.js";
import { initCopy } from "./copy.js";

const form = $("check-form");

async function check(event) {
  event.preventDefault();
  if (!canSubmit()) return;

  const hasRules = state.complianceFiles.length > 0;
  if (!hasRules) setComplianceError("Bitte laden Sie Ihre Compliance-Regeln hoch.");
  const hasPrompt = validatePrompt();

  if (!hasRules) return focusUpload();
  if (!hasPrompt) return focusPrompt();

  setBusy(true);
  showLoading();
  try {
    renderResult(await requestLlm(getPrompt(), concatComplianceFiles(state.complianceFiles)));
  } catch (error) {
    renderError(error);
  } finally {
    setBusy(false);
  }
}

function init() {
  $("demo-badge").hidden = !isMockMode();
  initUpload();
  initPromptField(form);
  initCopy();
  form.addEventListener("submit", check);
  setState("empty");
}

init();
