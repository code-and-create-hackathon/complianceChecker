// Finds the problems in the prompt. The API sends each problem's location as a text passage,
// not as a position (language models count characters unreliably), so the client searches for it.

/**
 * @typedef {import("../shared/validate.js").Problem & {
 *   id: number, start: number | null, end: number | null
 * }} LocatedProblem  start/end = position in the prompt, null if the passage could not be marked
 */

/**
 * Locates every problem and numbers them in reading order (problems without a position last).
 * Identical passages are matched in order (first problem → first occurrence, and so on).
 * Passages that do not occur, or that overlap an already located one, stay unmarked.
 * @param {string} prompt
 * @param {import("../shared/validate.js").Problem[]} problems
 * @returns {LocatedProblem[]}
 */
export function locateProblems(prompt, problems) {
  const taken = [];
  const nextSearchFrom = new Map(); // passage → where to look for its next occurrence

  const located = problems.map((problem, order) => {
    const passage = problem.location;
    if (!passage) return { ...problem, order, start: null, end: null };

    let from = nextSearchFrom.get(passage) ?? 0;
    while (true) {
      const start = prompt.indexOf(passage, from);
      if (start === -1) return { ...problem, order, start: null, end: null };
      const end = start + passage.length;
      nextSearchFrom.set(passage, end);
      if (!taken.some((t) => start < t.end && end > t.start)) {
        taken.push({ start, end });
        return { ...problem, order, start, end };
      }
      from = end; // overlaps a passage that is already marked: try the next occurrence
    }
  });

  return located
    .sort((a, b) => {
      if (a.start === null && b.start === null) return a.order - b.order;
      if (a.start === null) return 1;
      if (b.start === null) return -1;
      return a.start - b.start;
    })
    .map(({ order, ...problem }, index) => ({ ...problem, id: index + 1 }));
}
