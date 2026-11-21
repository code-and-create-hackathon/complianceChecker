// Reads the uploaded compliance files and concatenates them.
// No DOM access besides File objects: used by the website and the extension's options page.
// Website: nothing is persisted. Extension: rules are stored in chrome.storage.local.
import { COMPLIANCE_MAX_LENGTH, charLength } from "./validate.js";

/** @typedef {{name: string, text: string}} ComplianceFile */

/**
 * Reads the given files. Files that are empty or only whitespace are discarded.
 * Rejects if any file cannot be read.
 * @param {FileList | File[]} fileList
 * @returns {Promise<{files: ComplianceFile[], emptyNames: string[]}>}
 */
export async function readComplianceFiles(fileList) {
  const all = await Promise.all(
    [...fileList].map(async (file) => ({ name: file.name, text: await file.text() })),
  );
  return {
    files: all.filter((f) => f.text.trim()),
    emptyNames: all.filter((f) => !f.text.trim()).map((f) => f.name),
  };
}

/**
 * @param {ComplianceFile[]} files
 * @returns {string} all files as one Markdown string, each under its file name
 */
export function concatComplianceFiles(files) {
  return files.map((f) => `<!-- file: ${f.name} -->\n${f.text.trim()}`).join("\n\n---\n\n");
}

/**
 * Adds files to `current` in order (same name replaces the old file) as long as all rules
 * together stay within the API limit, measured exactly like validateRequest does.
 * @template {ComplianceFile} T
 * @param {T[]} current
 * @param {T[]} added
 * @returns {{files: T[], tooLong: string[]}} the new list and the names that did not fit
 */
export function addWithinLimit(current, added) {
  let files = current;
  const tooLong = [];
  for (const file of added) {
    const next = [...files.filter((f) => f.name !== file.name), file];
    if (charLength(concatComplianceFiles(next)) > COMPLIANCE_MAX_LENGTH) tooLong.push(file.name);
    else files = next;
  }
  return { files, tooLong };
}
