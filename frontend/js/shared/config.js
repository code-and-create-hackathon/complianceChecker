// Frontend configuration, the one place that defines the backend endpoint for website and extension.
// Not secret (visible in every browser). Demo mode: website via ?mock=1 / ?mock=0 (web/mock_mode.js),
// extension automatically while BACKEND_ORIGIN is empty.

// Where the backend runs. "" = same origin as the page: the website is served by the backend itself.
// The extension needs an absolute origin (e.g. "http://localhost:8080" locally, the AWS URL later) and derives
// its host permission from it; the backend allows the extension via CORS_ALLOWED_ORIGINS.
// TODO: set to the AWS backend once it is deployed.
export const BACKEND_ORIGIN = "";

export const API_URL = `${BACKEND_ORIGIN}/api/check`;

// The check gives up after this time, so the page never hangs on "Checking …".
export const REQUEST_TIMEOUT_MS = 60_000;
