// Frontend configuration. The API URL is not a secret (it is visible in every browser).
// Demo mode (no backend): website via ?mock=1 / ?mock=0 (web/mock_mode.js), extension via its options page.

// The one place that defines the backend endpoint, for website and extension alike.
// Absolute URL (the extension cannot use a relative one); the extension derives its host permission from it.
// If the website is served from another origin, the backend must allow it via CORS.
// TODO: set to the real AWS endpoint once it exists, e.g. "https://api.example.com/api/check".
// "" = no backend yet: the extension runs in demo mode, the website only works with ?mock=1 / on localhost.
export const API_URL = "";

// The check gives up after this time, so the page never hangs on "Checking …".
export const REQUEST_TIMEOUT_MS = 60_000;
