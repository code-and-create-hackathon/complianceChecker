// Word-level diff: which parts of the sanitised version were changed compared to the original prompt.

// Above this many table cells (prompt tokens × suggestion tokens) only placeholders are marked.
const MAX_CELLS = 4_000_000;
const PLACEHOLDER = /\[[^\]\s]+\]/g;

/** Words, single punctuation characters and whitespace runs as separate tokens. */
function tokenize(text) {
  return text.match(/\s+|[\p{L}\p{N}_]+|[^\s\p{L}\p{N}_]/gu) ?? [];
}

const isSpace = (token) => /^\s+$/.test(token);

/** Fallback for very long texts: mark only placeholders like [IBAN_1]. */
function placeholderSegments(after) {
  const segments = [];
  let pos = 0;
  for (const match of after.matchAll(PLACEHOLDER)) {
    if (match.index > pos) segments.push({ text: after.slice(pos, match.index), changed: false });
    segments.push({ text: match[0], changed: true });
    pos = match.index + match[0].length;
  }
  if (pos < after.length) segments.push({ text: after.slice(pos), changed: false });
  return segments;
}

/**
 * Splits `after` into segments that were taken over from `before` (changed: false)
 * and segments that are new or rewritten (changed: true). Joined, the segments give `after`.
 * @param {string} before  the original prompt
 * @param {string} after   the sanitised version
 * @returns {{text: string, changed: boolean}[]}
 */
export function changedSegments(before, after) {
  const a = tokenize(before);
  const b = tokenize(after);
  if (!b.length) return [];
  if ((a.length + 1) * (b.length + 1) > MAX_CELLS) return placeholderSegments(after);

  // Longest common subsequence of tokens, filled from the back.
  const lcs = Array.from({ length: a.length + 1 }, () => new Uint32Array(b.length + 1));
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }

  const kept = new Array(b.length).fill(false);
  for (let i = 0, j = 0; i < a.length && j < b.length; ) {
    if (a[i] === b[j]) {
      kept[j] = true;
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) i++;
    else j++;
  }

  // Whitespace is never marked on its own, only between two changed words (so a phrase is one block).
  const changed = b.map((token, k) => !kept[k] && !isSpace(token));
  for (let k = 1; k < b.length - 1; k++) {
    if (isSpace(b[k]) && changed[k - 1] && changed[k + 1]) changed[k] = true;
  }

  const segments = [];
  b.forEach((token, k) => {
    const last = segments[segments.length - 1];
    if (last && last.changed === changed[k]) last.text += token;
    else segments.push({ text: token, changed: changed[k] });
  });
  return segments;
}
