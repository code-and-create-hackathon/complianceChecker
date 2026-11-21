// Highlights the findings of the last check in the prompt field (red = critical, yellow = others).
// The marks are drawn on a layer behind the transparent textarea (see .prompt-highlights in style.css);
// the textarea itself stays a normal, editable field.
import { $ } from "./dom.js";
import { locateProblems } from "./locate.js";

const promptInput = $("prompt");
const layer = $("prompt-highlights");

function syncScroll() {
  layer.scrollTop = promptInput.scrollTop;
  layer.scrollLeft = promptInput.scrollLeft;
}

/**
 * Marks the problems' locations in the current prompt text.
 * @param {string} checkedPrompt  the (trimmed) prompt that was sent to the check
 * @param {import("../shared/validate.js").Problem[]} problems
 */
export function showHighlights(checkedPrompt, problems) {
  const text = promptInput.value;
  // The check gets the trimmed prompt; shift the positions by the whitespace that was cut off in front.
  const offset = Math.max(0, text.indexOf(checkedPrompt));

  const nodes = [];
  let pos = 0;
  const located = locateProblems(checkedPrompt, problems)
    .filter((p) => p.start !== null)
    .sort((a, b) => a.start - b.start);

  for (const p of located) {
    const start = p.start + offset;
    const end = p.end + offset;
    if (start > pos) nodes.push(document.createTextNode(text.slice(pos, start)));
    const mark = document.createElement("mark");
    mark.className = p.severity === "critical" ? "is-crit" : "is-warn";
    mark.textContent = text.slice(start, end);
    nodes.push(mark);
    pos = end;
  }
  // A trailing space keeps the layer as tall as the textarea when the text ends with a line break.
  nodes.push(document.createTextNode(`${text.slice(pos)} `));

  layer.replaceChildren(...nodes);
  syncScroll();
}

export function clearHighlights() {
  layer.replaceChildren();
}

export function initPromptHighlight() {
  promptInput.addEventListener("scroll", syncScroll);
}
