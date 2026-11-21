// Website only: decides demo mode from the page URL. The extension uses its own setting.

/**
 * Demo mode without backend (offline demo): only with ?mock=1. Without it the page always calls the backend, on
 * localhost too, so it never silently shows simulated results.
 */
export function isMockMode() {
  return new URLSearchParams(window.location.search).get("mock") === "1";
}
