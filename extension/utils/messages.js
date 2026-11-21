// Messages between the content scripts and the background service worker: the one place that
// defines their types and reply shapes. The content side only uses the senders below.

export const MESSAGE = {
  /** {type, prompt} → CheckReply */
  check: 'check',
  /** {type} → no reply. Opens the settings page: rules in demo mode, login with a backend. */
  openRules: 'openRules',
};

/**
 * @typedef {{ok: true, result: import('@shared/validate.js').CheckResponse, mock: boolean}
 *         | {ok: false, error: 'noRules' | 'notLoggedIn' | 'failed', message: string, mock: boolean}} CheckReply
 */

/** @returns {CheckReply} */
export function failedReply(message, mock = false) {
  return { ok: false, error: 'failed', message, mock };
}

/**
 * Asks the background to check `prompt`. Never rejects: a missing background becomes a failed reply.
 * @param {string} prompt
 * @returns {Promise<CheckReply>}
 */
export async function sendCheck(prompt) {
  try {
    return await browser.runtime.sendMessage({ type: MESSAGE.check, prompt });
  } catch (error) {
    // Typically "Extension context invalidated" after the extension was reloaded.
    console.warn('[compliance] background not reachable', error);
    return failedReply('The extension was updated or reloaded. Please reload this page.');
  }
}

export function sendOpenRules() {
  browser.runtime.sendMessage({ type: MESSAGE.openRules }).catch(() => {});
}
