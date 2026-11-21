// Shared by website and extension (page, service worker): no window/DOM access here.
// Payloads: see API.md.
import { API_URL, REQUEST_TIMEOUT_MS } from "./config.js";
import { mockLlm } from "./mock_llm.js";
import { validateRequest, validateResponse } from "./validate.js";

/**
 * @param {string} prompt
 * @param {string} compliance
 * @param {{mock?: boolean}} [options]  mock: use the local demo instead of the backend (API_URL).
 *   The caller decides: the website from its URL (web/mock_mode.js), the extension from BACKEND_ORIGIN.
 * @returns {Promise<import("./validate.js").CheckResponse>}
 * @throws {import("./validate.js").ValidationError} if request or response breaks the contract
 */
export async function requestLlm(prompt, compliance, { mock = false } = {}) {
  const request = validateRequest({ prompt, compliance });

  if (mock) return validateResponse(await mockLlm(request.prompt, request.compliance));

  // The timeout covers both the connection and reading the response body.
  const signal = AbortSignal.timeout(REQUEST_TIMEOUT_MS);
  let res;
  let payload;
  try {
    res = await fetch(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
      signal,
    });
    if (res.ok) payload = await res.json();
  } catch (error) {
    if (error.name === "TimeoutError") {
      throw new Error(
        `Timeout: the check service did not answer within ${REQUEST_TIMEOUT_MS / 1000} seconds.`,
      );
    }
    if (!res) throw new Error("The check service is not reachable.");
    throw new Error("The check service did not send a valid JSON response.");
  }
  if (!res.ok) throw new Error(`The check service answered with status ${res.status}.`);
  return validateResponse(payload);
}
