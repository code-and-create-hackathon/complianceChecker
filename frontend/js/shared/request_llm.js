// Shared by website and extension. Payloads: see API.md.

// TODO: set to the real backend endpoint once it exists.
const API_URL = "/api/check";

/**
 * @param {string} prompt
 * @param {string} compliance
 * @returns {Promise<{isSafe: boolean, problems: string[], improvedVersion: string}>}
 */
export async function requestLlm(prompt, compliance) {
  const res = await fetch(API_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ prompt, compliance }),
  });
  if (!res.ok) throw new Error(`Compliance check failed: ${res.status}`);
  return res.json();
}
