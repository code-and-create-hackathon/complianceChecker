// Compliance rules persisted in chrome.storage.local (stays on this device, max. 10 MB).
// Demo mode only. Written by the options page, read by the background check.
import { concatComplianceFiles } from '@shared/compliance_files.js';

const KEY = 'complianceFiles';

/** @returns {Promise<import('@shared/compliance_files.js').LoadedFile[]>} */
export async function getRuleFiles() {
  const data = await browser.storage.local.get(KEY);
  return data[KEY] ?? [];
}

/** @param {import('@shared/compliance_files.js').LoadedFile[]} files */
export async function setRuleFiles(files) {
  await browser.storage.local.set({ [KEY]: files });
}

/** @returns {Promise<string>} all rules as the `compliance` string of API.md ("" if none) */
export async function getComplianceText() {
  return concatComplianceFiles(await getRuleFiles());
}

/** Calls `callback(files)` whenever the rules change (e.g. in another options tab). */
export function onRuleFilesChanged(callback) {
  browser.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && KEY in changes) callback(changes[KEY].newValue ?? []);
  });
}
