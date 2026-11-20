// Shared by website and extension. Payloads: see API.md.
import { mockLlm } from "./mock_llm.js";

// TODO: set to the real backend endpoint once it exists.
const API_URL = "/api/check";

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
 * @returns {Promise<{isSafe: boolean, problems: string[], improvedVersion: string}>}
 */
export async function requestLlm(prompt, compliance) {
  if (isMockMode()) return mockLlm(prompt, compliance);

  let res;
  try {
    res = await fetch(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt, compliance }),
    });
  } catch {
    throw new Error("Der Prüfdienst ist nicht erreichbar.");
  }
  if (!res.ok) throw new Error(`Der Prüfdienst hat mit Status ${res.status} geantwortet.`);
  return res.json();
}
