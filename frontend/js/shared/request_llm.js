// Shared by website and extension (page, service worker): no window/DOM access here.
// Payloads: see API.md.
import { apiFetch } from "./api.js";
import { mockLlm } from "./mock_llm.js";
import { validateLocalRequest, validateRequest, validateResponse } from "./validate.js";

/**
 * Checks a prompt. With the backend, the rules are the company policy the administrator published; the client
 * only sends the prompt. In demo mode, the check runs locally against the rules passed in `compliance`.
 * @param {string} prompt
 * @param {{mock?: boolean, compliance?: string, token?: string, origin?: string}} [options]
 *   mock: use the local demo instead of the backend. The caller decides: the website from its URL
 *   (web/mock_mode.js), the extension from BACKEND_ORIGIN.
 *   compliance: demo mode only, the locally uploaded rules.
 *   token: backend only, the login token.
 *   origin: backend only; "" (website: the backend the page was loaded from), BACKEND_ORIGIN for the extension.
 * @returns {Promise<import("./validate.js").CheckResponse>}
 * @throws {import("./validate.js").ValidationError} if request or response breaks the contract
 * @throws {import("./api.js").ApiError} if the backend answers with an error (e.g. 401 not logged in)
 */
export async function requestLlm(prompt, { mock = false, compliance = "", token = "", origin = "" } = {}) {
  if (mock) {
    const request = validateLocalRequest({ prompt, compliance });
    return validateResponse(await mockLlm(request.prompt, request.compliance));
  }
  const request = validateRequest({ prompt });
  return validateResponse(await apiFetch("/api/check", { method: "POST", body: request, token, origin }));
}
