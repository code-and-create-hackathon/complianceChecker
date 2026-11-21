// Shared by website and extension. Payloads: see API.md.
import { API_URL, REQUEST_TIMEOUT_MS } from "./config.js";
import { validateRequest, validateResponse } from "./validate.js";

/**
 * @param {string} prompt
 * @param {string} compliance
 * @returns {Promise<import("./validate.js").CheckResponse>}
 * @throws {import("./validate.js").ValidationError} if request or response breaks the contract
 */
export async function requestLlm(prompt, compliance) {
  const request = validateRequest({ prompt, compliance });

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
    else {
      const errorPayload = await res.json().catch(() => null);
      const messages = {
        LLM_ACCESS_DENIED: "AWS verweigert den Nova-Zugriff. Prüfe die Bedrock-Berechtigung und Zugangsdaten.",
        LLM_AWS_ERROR: "Amazon Bedrock hat die Anfrage abgelehnt. Prüfe die Ausgabe im Terminal.",
        LLM_CONNECTION_ERROR: "Amazon Bedrock ist nicht erreichbar oder die Zugangsdaten sind ungültig.",
        LLM_NOT_CONNECTED: "Bitte zuerst die AWS-Zugangsdaten in der Nova-Konsole eingeben.",
        LLM_SIMULATED_RESULT: "Ein Demo-Ergebnis wurde verworfen. Bitte starten Sie die Nova-Live-Version.",
        LLM_INVALID_RESPONSE: "Nova hat kein gültiges Prüfergebnis geliefert. Bitte erneut versuchen.",
      };
      throw new Error(errorPayload?.message || messages[errorPayload?.error] || `Der Prüfdienst hat mit Status ${res.status} geantwortet.`);
    }
  } catch (error) {
    if (res && !res.ok) throw error;
    if (error.name === "TimeoutError") {
      throw new Error(
        `Zeitüberschreitung: Der Prüfdienst hat nicht innerhalb von ${REQUEST_TIMEOUT_MS / 1000} Sekunden geantwortet.`,
      );
    }
    if (!res) throw new Error("Der Prüfdienst ist nicht erreichbar.");
    throw new Error("Der Prüfdienst hat keine gültige JSON-Antwort gesendet.");
  }
  if (!res.ok) throw new Error(`Der Prüfdienst hat mit Status ${res.status} geantwortet.`);
  return validateResponse(payload);
}
