// Reads the uploaded compliance files and concatenates them.
// Nothing is persisted: the user uploads the rules again on every visit.

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
