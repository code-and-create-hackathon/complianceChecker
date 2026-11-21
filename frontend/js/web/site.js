// Website entry point (Stage 1): wires up the modules and runs the check.
// Demo mode (no backend): rules are uploaded in the browser, no accounts.
// Backend mode: login first; checks always use the company policy the administrator published.
import { requestLlm } from "../shared/request_llm.js";
import { isMockMode } from "./mock_mode.js";
import { concatComplianceFiles } from "../shared/compliance_files.js";
import { $, announce } from "./dom.js";
import { state, canSubmit, setBusy, isCurrent } from "./state.js";
import { setState, showLoading, renderResult, renderError, resetResult } from "./result_view.js";
import { initUpload, setComplianceError, focusUpload, clearFiles } from "./upload.js";
import { initPromptField, getPrompt, validatePrompt, focusPrompt } from "./prompt_field.js";
import { initPromptHighlight } from "./prompt_highlight.js";
import { initCopy } from "./copy.js";
import { getToken, isAdmin, restoreSession, forgetSession } from "./company_accounts.js";
import { initAccountView, showLogin, showAccount } from "./account_view.js";
import { initPolicyView, showPolicyView, updatePublishButton } from "./policy_view.js";
import { initTeamView, showTeam } from "./team_view.js";

const form = $("check-form");
const workspace = $("workspace");
const mock = isMockMode();

async function check(event) {
  event.preventDefault();
  if (!canSubmit()) return;

  // With the backend the rules are the published policy, so only demo mode needs uploaded files.
  const hasRules = !mock || state.complianceFiles.length > 0;
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
    response = await requestLlm(request.prompt, mock
      ? { mock, compliance: concatComplianceFiles(request.files) }
      : { token: getToken() });
  } catch (error) {
    failure = error;
  }
  setBusy(false);

  if (failure?.status === 401) {
    sessionExpired();
    return;
  }
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

/** @param {import("./company_accounts.js").Session} session */
function enterApp(session) {
  showAccount(session);
  workspace.hidden = false;
  showPolicyView(session);
  showTeam(isAdmin(session));
}

function leaveApp() {
  workspace.hidden = true;
  showTeam(false);
  clearFiles();
  resetResult();
}

function sessionExpired() {
  forgetSession();
  leaveApp();
  showLogin("Your session has expired. Please log in again.");
}

async function initAccounts() {
  initAccountView({ onLoggedIn: enterApp, onLoggedOut: leaveApp });
  initPolicyView();
  initTeamView();
  try {
    const session = await restoreSession();
    if (session) enterApp(session);
    else showLogin();
  } catch (error) {
    showLogin(error.message);
  }
}

function init() {
  $("demo-badge").hidden = !mock;
  initUpload({ onFilesChanged: mock ? undefined : updatePublishButton });
  initPromptField(form);
  initPromptHighlight();
  initCopy();
  form.addEventListener("submit", check);
  setState("empty");
  if (mock) workspace.hidden = false;
  else initAccounts();
}

init();
