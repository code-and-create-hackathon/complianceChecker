// Service worker: runs the compliance check for the content scripts.
// The fetch happens here (not in the content script) so the chat site's CSP/CORS cannot block it.
import { API_URL } from '@shared/config.js';
import { requestLlm } from '@shared/request_llm.js';
import { getComplianceText } from '@/utils/rules_store.js';
import { MESSAGE, failedReply } from '@/utils/messages.js';

/** @param {string} prompt @returns {Promise<import('@/utils/messages.js').CheckReply>} */
async function check(prompt) {
  const mock = !API_URL; // no backend configured yet → local demo check
  const compliance = await getComplianceText();
  if (!compliance.trim()) {
    return { ok: false, error: 'noRules', message: 'No compliance rules loaded.', mock };
  }
  try {
    const result = await requestLlm(prompt, compliance, { mock });
    return { ok: true, result, mock };
  } catch (error) {
    console.warn('[compliance] check failed', error);
    return failedReply(error.message || String(error), mock);
  }
}

export default defineBackground(() => {
  console.log('[compliance] background started', { id: browser.runtime.id, mock: !API_URL });

  // Toolbar icon → rules page.
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
