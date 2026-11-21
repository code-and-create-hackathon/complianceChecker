// Login session of the extension (backend mode only), persisted in chrome.storage.local on this device.
// Written by the options page (login/logout), read by the background check.
// TODO(security): demo login, see the backend's AuthService. Replace with the company's SSO before real use.

const KEY = 'session';

/** @typedef {{token: string, username: string, role: 'admin' | 'worker'}} Session */

/** @returns {Promise<Session | null>} */
export async function getSession() {
  const data = await browser.storage.local.get(KEY);
  return data[KEY]?.token ? data[KEY] : null;
}

/** @param {Session} session */
export async function setSession(session) {
  await browser.storage.local.set({ [KEY]: session });
}

export async function clearSession() {
  await browser.storage.local.remove(KEY);
}
