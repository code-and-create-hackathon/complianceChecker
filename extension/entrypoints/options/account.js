// Backend mode: log in, show the active company policy, log out.
// Checks always run against the policy the administrator published; there are no local rules.
import { apiFetch, ApiError } from '@shared/api.js';
import { BACKEND_ORIGIN } from '@shared/config.js';
import { ruleSummary } from '@shared/compliance_files.js';
import { getSession, setSession, clearSession } from '@/utils/session_store.js';

const $ = (id) => document.getElementById(id);

function showError(text) {
  $('login-error').textContent = text;
  $('login-error').hidden = !text;
}

async function showPolicy(token) {
  const status = $('policy-status');
  try {
    const policy = await apiFetch('/api/policy', { token, origin: BACKEND_ORIGIN });
    status.textContent = `Company policy version ${policy.version} (${ruleSummary(policy.text)}), `
      + `published by ${policy.publishedBy}.`;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      await clearSession();
      render(null, 'Your session has expired. Please log in again.');
      return;
    }
    status.textContent = error instanceof ApiError && error.code === 'NO_POLICY'
      ? 'No company policy has been published yet: prompts cannot be checked.'
      : `The company policy could not be loaded: ${error.message}`;
  }
}

/** @param {import('@/utils/session_store.js').Session | null} session @param {string} [message] */
function render(session, message = '') {
  $('login-form').hidden = Boolean(session);
  $('logged-in').hidden = !session;
  showError(message);
  if (session) {
    $('account-name').textContent = session.username;
    showPolicy(session.token);
  } else {
    $('login-username').focus();
  }
}

async function login(event) {
  event.preventDefault();
  const username = $('login-username').value.trim();
  if (!username) {
    showError('Please enter your username.');
    return;
  }
  const adminCode = $('login-code').value;
  $('login-submit').disabled = true;
  showError('');
  try {
    const answer = await apiFetch('/api/auth/login', {
      method: 'POST',
      origin: BACKEND_ORIGIN,
      body: adminCode ? { username, adminCode } : { username },
    });
    const session = { token: answer.token, username: answer.username, role: answer.role };
    await setSession(session);
    $('login-code').value = '';
    render(session);
  } catch (error) {
    showError(error.message || 'Login failed.');
  } finally {
    $('login-submit').disabled = false;
  }
}

async function logout() {
  const session = await getSession();
  await clearSession();
  render(null);
  if (session) await apiFetch('/api/auth/logout', { method: 'POST', token: session.token, origin: BACKEND_ORIGIN }).catch(() => {});
}

export async function initAccount() {
  $('account-section').hidden = false;
  $('login-form').addEventListener('submit', login);
  $('logout').addEventListener('click', logout);
  render(await getSession());
}
