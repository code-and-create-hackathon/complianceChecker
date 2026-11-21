// Loading, merging and describing the uploaded compliance files.
// No DOM access besides File objects: used by the website and the extension's options page.
// Website: nothing is persisted. Extension: rules are stored in chrome.storage.local.
import { COMPLIANCE_MAX_LENGTH, charLength } from "./validate.js";
import { numberFormat, plural } from "./format.js";

/** @typedef {{name: string, text: string}} ComplianceFile */
/** @typedef {ComplianceFile & {size: number}} LoadedFile  size in bytes, for display */

/** File types accepted by the upload (also the `accept` attribute of the file inputs). */
export const ACCEPTED_FILE = /\.(md|markdown|txt)$/i;

const TOO_LONG = `rules exceed ${numberFormat.format(COMPLIANCE_MAX_LENGTH)} characters in total`;

/**
 * Reads the picked files. Wrong types, files that are too big for sure and empty files are skipped.
 * Rejects if any file cannot be read.
 * @param {FileList | File[]} picked
 * @returns {Promise<{loaded: LoadedFile[], skipped: string[]}>}  skipped: "name (reason)"
 */
export async function loadComplianceFiles(picked) {
  const skipped = [];
  const candidates = [...picked].filter((file) => {
    if (!ACCEPTED_FILE.test(file.name)) skipped.push(`${file.name} (not .md/.txt)`);
    // UTF-8 needs at most 4 bytes per character: anything bigger is too long for sure, skip reading it.
    else if (file.size > COMPLIANCE_MAX_LENGTH * 4) skipped.push(`${file.name} (${TOO_LONG})`);
    else return true;
    return false;
  });

  const read = await Promise.all(
    candidates.map(async (file) => ({ name: file.name, text: await file.text(), size: file.size })),
  );
  const loaded = read.filter((file) => {
    if (file.text.trim()) return true;
    skipped.push(`${file.name} (empty)`);
    return false;
  });
  return { loaded, skipped };
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
 * together stay within the API limit, measured exactly like validateLocalRequest does.
 * @template {ComplianceFile} T
 * @param {T[]} current
 * @param {T[]} added
 * @returns {{files: T[], skipped: string[]}} the new list and "name (reason)" for files that did not fit
 */
export function addWithinLimit(current, added) {
  let files = current;
  const skipped = [];
  for (const file of added) {
    const next = [...files.filter((f) => f.name !== file.name), file];
    if (charLength(concatComplianceFiles(next)) > COMPLIANCE_MAX_LENGTH) skipped.push(`${file.name} (${TOO_LONG})`);
    else files = next;
  }
  return { files, skipped };
}

/** @param {string[]} skipped  @returns {string} the notice for skipped files ("" if none) */
export function skippedMessage(skipped) {
  return skipped.length ? `Not added: ${skipped.join(", ")}.` : "";
}

function countRules(markdown) {
  return markdown.split(/\r?\n/).filter((line) => /^\s*(?:[-*+]|\d+[.)])\s+\S/.test(line)).length;
}

/** "12 rules" for Markdown lists, "rule set" for free text. */
export function ruleSummary(text) {
  const rules = countRules(text);
  return rules > 0 ? plural(rules, "rule", "rules") : "rule set";
}
