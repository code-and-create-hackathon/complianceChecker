// Website only: decides demo mode from the page URL. The extension uses its own setting.

/**
 * Demo mode without backend: on with ?mock=1, off with ?mock=0.
 * Without the parameter it is on automatically on localhost / 127.0.0.1 (e.g. Live Server).
 */
export function isMockMode() {
  const param = new URLSearchParams(window.location.search).get("mock");
  if (param !== null) return param !== "0";
  return ["localhost", "127.0.0.1"].includes(window.location.hostname);
}
