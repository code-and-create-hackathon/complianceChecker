// Frontend configuration. The API URL is not a secret (it is visible in every browser).
// Demo mode (no backend) is switched via ?mock=1 / ?mock=0, see isMockMode() in request_llm.js.

// TODO: set to the real backend endpoint once it exists.
export const API_URL = "/api/check";

// The check gives up after this time, so the page never hangs on "Wird geprüft …".
export const REQUEST_TIMEOUT_MS = 60_000;
