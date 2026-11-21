// Frontend configuration, the one place that defines the backend endpoint for website and extension.
// Not secret (visible in every browser). Offline demo mode (browser-only mock): website only with ?mock=1
// (web/mock_mode.js), extension while BACKEND_ORIGIN is empty.

// Where the backend runs, as seen by the EXTENSION: it is not served by the backend, so it needs an absolute origin
// ("http://localhost:8080" locally, the AWS URL later) and derives its host permission from it; the backend also
// allows it via CORS_ALLOWED_ORIGINS. "" = demo mode (local mock check, nothing leaves the browser).
// The website ignores this value: it is served by the backend and calls it on its own origin, see API_URL.
// TODO: set to the AWS backend once it is deployed.
export const BACKEND_ORIGIN = "http://localhost:8080";

export const API_PATH = "/api/check";

/** Website: relative, i.e. the backend the page was loaded from (works on any host or port). */
export const API_URL = API_PATH;

// The check gives up after this time, so the page never hangs on "Checking …".
export const REQUEST_TIMEOUT_MS = 60_000;
