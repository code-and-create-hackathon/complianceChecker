// Service worker: runs the compliance check for the content scripts.
// The fetch happens here (not in the content script) so the chat site's CSP/CORS cannot block it.
import { BACKEND_ORIGIN } from '@shared/config.js';
import { ApiError } from '@shared/api.js';
import { requestLlm } from '@shared/request_llm.js';
import { getComplianceText } from '@/utils/rules_store.js';
import { getSession, clearSession } from '@/utils/session_store.js';
import { MESSAGE, failedReply } from '@/utils/messages.js';

/** Demo mode: the extension can only reach an absolute origin; none yet → local check with local rules. */
const MOCK = !BACKEND_ORIGIN;

/** @returns {import('@/utils/messages.js').CheckReply} */
function notLoggedIn() {
  return { ok: false, error: 'notLoggedIn', message: 'Please log in on the settings page.', mock: false };
}

/** @param {string} prompt @returns {Promise<import('@/utils/messages.js').CheckReply>} */
async function checkLocally(prompt) {
  const compliance = await getComplianceText();
  if (!compliance.trim()) {
    return { ok: false, error: 'noRules', message: 'No compliance rules loaded.', mock: true };
  }
  const result = await requestLlm(prompt, { mock: true, compliance });
  return { ok: true, result, mock: true };
}

/** Backend mode: the backend checks against the company policy; we only send the prompt and the login token. */
async function checkWithBackend(prompt) {
  const session = await getSession();
  if (!session) return notLoggedIn();
  try {
    const result = await requestLlm(prompt, { token: session.token, origin: BACKEND_ORIGIN });
    return { ok: true, result, mock: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      await clearSession(); // expired or the backend restarted
      return notLoggedIn();
    }
    throw error;
  }
}

/** @param {string} prompt @returns {Promise<import('@/utils/messages.js').CheckReply>} */
async function check(prompt) {
  try {
    return await (MOCK ? checkLocally(prompt) : checkWithBackend(prompt));
  } catch (error) {
    console.warn('[compliance] check failed', error);
    return failedReply(error.message || String(error), MOCK);
  }
}

export default defineBackground(() => {
  console.log('[compliance] background started', { id: browser.runtime.id, mock: MOCK });

  // Toolbar icon → settings page (rules in demo mode, login with a backend).
  browser.action.onClicked.addListener(() => browser.runtime.openOptionsPage());

  browser.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message?.type === MESSAGE.check) {
      check(String(message.prompt ?? '')).then(sendResponse);
      return true; // keeps the channel open for the async answer
    }
    if (message?.type === MESSAGE.openRules) {
      browser.runtime.openOptionsPage();
    }
    return false;
  });
});
