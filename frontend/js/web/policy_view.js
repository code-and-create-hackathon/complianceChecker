// Field 1 in backend mode: the company policy.
// Workers see the published policy (read only). The administrator also uploads files and publishes them.
import { apiFetch, ApiError } from "../shared/api.js";
import { concatComplianceFiles, ruleSummary } from "../shared/compliance_files.js";
import { validateLocalRequest } from "../shared/validate.js";
import { getToken, isAdmin } from "./company_accounts.js";
import { $, announce } from "./dom.js";
import { state } from "./state.js";
import { clearFiles } from "./upload.js";

const policyInfo = $("policy-info");
const policyStatus = $("policy-status");
const policyDetails = $("policy-details");
const policyText = $("policy-text");
const publishBox = $("policy-publish");
const publishButton = $("policy-publish-button");
const publishMessage = $("policy-publish-message");

const dateFormat = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" });

/** @typedef {{version: number, text: string, publishedAt: string, publishedBy: string}} Policy */

/** @param {Policy | null} policy */
function renderPolicy(policy) {
  policyInfo.hidden = false;
  policyDetails.hidden = !policy;
  if (!policy) {
    policyStatus.textContent = "No company policy has been published yet.";
    policyText.textContent = "";
    return;
  }
  const when = dateFormat.format(new Date(policy.publishedAt));
  policyStatus.textContent =
    `Version ${policy.version} · ${ruleSummary(policy.text)} · published by ${policy.publishedBy}, ${when}`;
  policyText.textContent = policy.text;
}

/** Loads the active policy from the backend and shows it. */
export async function loadPolicy() {
  try {
    renderPolicy(await apiFetch("/api/policy", { token: getToken() }));
  } catch (error) {
    if (error instanceof ApiError && error.code === "NO_POLICY") renderPolicy(null);
    else {
      policyInfo.hidden = false;
      policyDetails.hidden = true;
      policyStatus.textContent = `The company policy could not be loaded: ${error.message}`;
    }
  }
}

/** Enables "Publish" once the administrator has uploaded files. Call after every change of the files. */
export function updatePublishButton() {
  publishButton.disabled = state.complianceFiles.length === 0;
}

async function publish() {
  const text = concatComplianceFiles(state.complianceFiles);
  try {
    // Same limit as the backend; the prompt is a placeholder, only the rules are checked here.
    validateLocalRequest({ prompt: "-", compliance: text });
  } catch (error) {
    publishMessage.textContent = error.message;
    return;
  }
  publishButton.disabled = true;
  publishMessage.textContent = "Publishing …";
  try {
    const policy = await apiFetch("/api/policy", { method: "PUT", body: { text }, token: getToken() });
    renderPolicy(policy);
    clearFiles();
    publishMessage.textContent = `Published as version ${policy.version}. All checks now use it.`;
    announce(publishMessage.textContent);
  } catch (error) {
    publishMessage.textContent = `Not published: ${error.message}`;
  } finally {
    updatePublishButton();
  }
}

/**
 * Switches field 1 to the company policy for the logged-in user.
 * @param {import("./company_accounts.js").Session} session
 */
export function showPolicyView(session) {
  const admin = isAdmin(session);
  $("rules-label").textContent = "Company policy";
  $("rules-upload").hidden = !admin;
  publishBox.hidden = !admin;
  publishMessage.textContent = "";
  updatePublishButton();
  loadPolicy();
}

export function initPolicyView() {
  publishButton.addEventListener("click", publish);
}
