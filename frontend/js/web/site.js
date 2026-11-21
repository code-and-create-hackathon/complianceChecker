// Website entry point (Stage 1): wires up the modules and runs the check.
import { requestLlm } from "../shared/request_llm.js";
import { isMockMode } from "./mock_mode.js";
import { concatComplianceFiles } from "../shared/compliance_files.js";
import { $, announce } from "./dom.js";
import { state, canSubmit, setBusy, isCurrent } from "./state.js";
import { setState, showLoading, renderResult, renderError, resetResult } from "./result_view.js";
import { initUpload, setComplianceError, focusUpload } from "./upload.js";
import { initPromptField, getPrompt, validatePrompt, focusPrompt } from "./prompt_field.js";
import { initPromptHighlight } from "./prompt_highlight.js";
import { initCopy } from "./copy.js";

const form = $("check-form");

async function check(event) {
  event.preventDefault();
  if (!canSubmit()) return;

  const hasRules = state.complianceFiles.length > 0;
  if (!hasRules) setComplianceError("Please upload your compliance rules.");
  const hasPrompt = validatePrompt();

  if (!hasRules) return focusUpload();
  if (!hasPrompt) return focusPrompt();

  /** @type {import("./state.js").CheckRequest} */
  const request = { prompt: getPrompt(), files: state.complianceFiles };
  setBusy(true);
  showLoading();
  let response;
  let failure;
  try {
    response = await requestLlm(request.prompt, concatComplianceFiles(request.files), { mock: isMockMode() });
  } catch (error) {
    failure = error;
  }
  setBusy(false);

  // Prompt or rules changed while the check ran: the answer belongs to something else, drop it.
  if (!isCurrent(request, getPrompt())) {
    resetResult();
    announce("The prompt or the rules changed during the check. Please check again.");
    return;
  }
  if (failure) {
    renderError(failure);
    return;
  }
  state.result = { request, response };
  renderResult();
}

function init() {
  $("demo-badge").hidden = !isMockMode();
  initUpload();
  initPromptField(form);
  initPromptHighlight();
  initCopy();
  form.addEventListener("submit", check);
  setState("empty");
}

init();
