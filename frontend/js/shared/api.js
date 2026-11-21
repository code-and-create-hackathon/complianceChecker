// Shared by website and extension (page, service worker): no window/DOM access here.
// The one place that talks to the backend's /api. Endpoints and payloads: see API.md.
import { REQUEST_TIMEOUT_MS } from "./config.js";

/** The backend answered with an error status. `code` is the stable error code from API.md, e.g. "NO_POLICY". */
export class ApiError extends Error {
  /** @param {string} message @param {number} status @param {string} code */
  constructor(message, status, code) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

/**
 * Sends a JSON request to the backend and returns the JSON answer (null for 204 No Content).
 * @param {string} path  e.g. "/api/check"
 * @param {{method?: string, body?: unknown, token?: string, origin?: string}} [options]
 *   token: the login token, if any.
 *   origin: where the backend runs. "" (default) = the origin the page was loaded from (website);
 *   the extension passes BACKEND_ORIGIN, because it is not served by the backend.
 * @returns {Promise<any>}
 * @throws {ApiError} on an error status, with the backend's own message; Error if the backend is not reachable
 */
export async function apiFetch(path, { method = "GET", body, token, origin = "" } = {}) {
  /** @type {Record<string, string>} */
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;

  // The timeout covers both the connection and reading the response body.
  const signal = AbortSignal.timeout(REQUEST_TIMEOUT_MS);
  let res;
  let payload = null;
  try {
    res = await fetch(`${origin}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
    if (res.status !== 204) payload = await res.json();
  } catch (error) {
    if (error.name === "TimeoutError") {
      throw new Error(`Timeout: the check service did not answer within ${REQUEST_TIMEOUT_MS / 1000} seconds.`);
    }
    if (!res) throw new Error("The check service is not reachable.");
    if (res.ok) throw new Error("The check service did not send a valid JSON response.");
    // Error without a JSON body: handled below with a generic message.
  }
  if (!res.ok) {
    const message = typeof payload?.message === "string" ? payload.message : "";
    throw new ApiError(
      message || `The check service answered with status ${res.status}.`,
      res.status,
      typeof payload?.error === "string" ? payload.error : "",
    );
  }
  return payload;
}
