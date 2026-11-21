// Frontend configuration. The API URL is not a secret (it is visible in every browser).
// Demo mode (no backend): website via ?mock=1 / ?mock=0 (web/mock_mode.js), extension via its options page.

// TODO: set to the real backend endpoint once it exists.
// Website default (relative). The extension needs an absolute URL, see extension/.
export const API_URL = "/api/check";

// The check gives up after this time, so the page never hangs on "Checking …".
export const REQUEST_TIMEOUT_MS = 60_000;
