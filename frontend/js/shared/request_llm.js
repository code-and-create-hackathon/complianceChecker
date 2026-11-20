// Shared by website and extension. Payloads: see API.md.
import { API_URL } from "./config.js";
import { mockLlm } from "./mock_llm.js";
import { validateRequest, validateResponse } from "./validate.js";

/**
 * Demo-Modus ohne Backend: an mit ?mock=1, aus mit ?mock=0.
 * Ohne Parameter automatisch an auf localhost / 127.0.0.1 (z. B. Live Server).
 */
export function isMockMode() {
  const param = new URLSearchParams(window.location.search).get("mock");
  if (param !== null) return param !== "0";
  return ["localhost", "127.0.0.1"].includes(window.location.hostname);
}

/**
 * @param {string} prompt
 * @param {string} compliance
 * @returns {Promise<import("./validate.js").CheckResponse>}
 * @throws {import("./validate.js").ValidationError} if request or response breaks the contract
 */
export async function requestLlm(prompt, compliance) {
  const request = validateRequest({ prompt, compliance });

  if (isMockMode()) return validateResponse(await mockLlm(request.prompt, request.compliance));

  let res;
  try {
    res = await fetch(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
  } catch {
    throw new Error("Der Prüfdienst ist nicht erreichbar.");
  }
  if (!res.ok) throw new Error(`Der Prüfdienst hat mit Status ${res.status} geantwortet.`);
  return validateResponse(await res.json());
}
