// Company accounts: the login session of the website (backend mode only; demo mode has no accounts).
// TODO(security): demo login, see the backend's AuthService: workers log in by username only, the administrator
// also needs the admin code. Replace with the company's identity provider (SSO) before real use.
import { apiFetch, ApiError } from "../shared/api.js";

/** @typedef {"admin" | "worker"} Role */
/** @typedef {{token: string, username: string, role: Role}} Session */

// sessionStorage: one login per tab, gone when the tab is closed.
const KEY = "complianceChecker.session";

/** @returns {Session | null} */
export function getSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(KEY) ?? "null");
    return session?.token ? session : null;
  } catch {
    return null;
  }
}

/** @param {Session | null} session */
function saveSession(session) {
  try {
    if (session) sessionStorage.setItem(KEY, JSON.stringify(session));
    else sessionStorage.removeItem(KEY);
  } catch {
    // Storage blocked (e.g. private mode): the login then lasts until the page is reloaded.
  }
}

/** @returns {string} the token for API calls ("" if not logged in) */
export function getToken() {
  return getSession()?.token ?? "";
}

/** @param {Session | null} session */
export function isAdmin(session) {
  return session?.role === "admin";
}

/**
 * @param {string} username
 * @param {string} adminCode  only needed for the administrator ("" for workers)
 * @returns {Promise<Session>}
 */
export async function login(username, adminCode) {
  const body = adminCode ? { username, adminCode } : { username };
  const answer = await apiFetch("/api/auth/login", { method: "POST", body });
  const session = { token: answer.token, username: answer.username, role: answer.role };
  saveSession(session);
  return session;
}

/** Forgets the session locally right away and tells the backend (best effort). */
export async function logout() {
  const token = getToken();
  saveSession(null);
  if (token) await apiFetch("/api/auth/logout", { method: "POST", token }).catch(() => {});
}

/** Forgets the session locally only, e.g. after the backend rejected the token. */
export function forgetSession() {
  saveSession(null);
}

/**
 * The stored session if the backend still accepts its token (it forgets all tokens on restart), otherwise null.
 * @returns {Promise<Session | null>}
 * @throws {Error} if the backend is not reachable
 */
export async function restoreSession() {
  const session = getSession();
  if (!session) return null;
  try {
    const me = await apiFetch("/api/auth/me", { token: session.token });
    return { ...session, username: me.username, role: me.role };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      saveSession(null);
      return null;
    }
    throw error;
  }
}
