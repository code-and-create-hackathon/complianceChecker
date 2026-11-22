// Login screen and the account area in the top bar (backend mode only).
import { login, logout, isAdmin } from "./company_accounts.js";
import { $, announce } from "./dom.js";

const loginView = $("login-view");
const loginForm = $("login-form");
const usernameInput = $("login-username");
const codeInput = $("login-code");
const loginError = $("login-error");
const loginSubmit = $("login-submit");
const account = $("account");

/** @type {(session: import("./company_accounts.js").Session) => void} */
let onLoggedIn = () => {};
/** @type {() => void} */
let onLoggedOut = () => {};

function setLoginError(message) {
  loginError.textContent = message;
  loginError.hidden = !message;
  if (message) usernameInput.setAttribute("aria-invalid", "true");
  else usernameInput.removeAttribute("aria-invalid");
}

/** Shows the login screen; `message` explains why (e.g. "Your session has expired."). */
export function showLogin(message = "") {
  account.hidden = true;
  loginView.hidden = false;
  codeInput.value = "";
  setLoginError(message);
  usernameInput.focus();
}

/** @param {import("./company_accounts.js").Session} session */
export function showAccount(session) {
  loginView.hidden = true;
  $("account-name").textContent = session.username;
  $("account-role").textContent = isAdmin(session) ? "Administrator" : "Worker";
  account.hidden = false;
}

async function submit(event) {
  event.preventDefault();
  const username = usernameInput.value.trim();
  if (!username) {
    setLoginError("Please enter your username.");
    usernameInput.focus();
    return;
  }
  loginSubmit.disabled = true;
  setLoginError("");
  try {
    const session = await login(username, codeInput.value);
    codeInput.value = "";
    showAccount(session);
    announce(`Logged in as ${session.username}.`);
    onLoggedIn(session);
  } catch (error) {
    setLoginError(error.message || "Login failed.");
  } finally {
    loginSubmit.disabled = false;
  }
}

/**
 * @param {{onLoggedIn: (session: import("./company_accounts.js").Session) => void, onLoggedOut: () => void}} handlers
 */
export function initAccountView(handlers) {
  ({ onLoggedIn, onLoggedOut } = handlers);
  loginForm.addEventListener("submit", submit);
  $("logout").addEventListener("click", async () => {
    await logout();
    onLoggedOut();
    showLogin();
    announce("Logged out.");
  });
}
