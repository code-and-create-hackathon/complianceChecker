// Read view of the editor: after a check the prompt is shown with its findings marked.
// A click into the text (or Enter) switches back to editing; the marks disappear until the next check.
import { $ } from "./dom.js";

const textarea = $("prompt");
const reader = $("reader");

/** The prompt as it was checked (the positions of the findings refer to it). */
let checkedText = "";

/** Findings with usable positions: inside the text, sorted, not overlapping. */
function usableFindings(text, findings) {
  const sorted = findings
    .filter((f) => Number.isInteger(f.start) && Number.isInteger(f.end) && f.start >= 0 && f.end <= text.length && f.start < f.end)
    .sort((a, b) => a.start - b.start);
  const result = [];
  let last = 0;
  for (const f of sorted) {
    if (f.start < last) continue;
    result.push(f);
    last = f.end;
  }
  return result;
}

function render(findings) {
  const nodes = [];
  let pos = 0;
  for (const f of findings) {
    if (f.start > pos) nodes.push(document.createTextNode(checkedText.slice(pos, f.start)));
    const severity = f.severity === "warn" ? "is-warn" : "is-crit";

    const mark = document.createElement("mark");
    mark.className = severity;
    mark.dataset.id = String(f.id);
    mark.textContent = checkedText.slice(f.start, f.end);

    const number = document.createElement("sup");
    number.className = `find-num ${severity}`;
    number.dataset.id = String(f.id);
    number.textContent = String(f.id);

    nodes.push(mark, number);
    pos = f.end;
  }
  if (pos < checkedText.length) nodes.push(document.createTextNode(checkedText.slice(pos)));
  reader.replaceChildren(...nodes);
}

/**
 * Shows the read view with the findings marked.
 * @returns {boolean} false (and the editor stays) if there is nothing to mark
 */
export function showReader(text, findings) {
  checkedText = text;
  const usable = usableFindings(text, findings);
  if (!usable.length) {
    showEditor();
    return false;
  }
  render(usable);
  textarea.hidden = true;
  reader.hidden = false;
  return true;
}

export function isReading() {
  return !reader.hidden;
}

/** Back to the textarea; optionally focus it and put the caret at an offset. */
export function showEditor({ focus = false, caret = null } = {}) {
  reader.hidden = true;
  textarea.hidden = false;
  if (!focus) return;
  textarea.focus();
  if (caret !== null) textarea.setSelectionRange(caret, caret);
}

/** Outlines the mark of the selected finding (null = none) and scrolls it into view. */
export function setActiveMark(id) {
  reader.querySelectorAll("mark").forEach((mark) => {
    mark.classList.toggle("is-active", mark.dataset.id === String(id));
  });
  if (id !== null) reader.querySelector(`mark[data-id="${id}"]`)?.scrollIntoView({ block: "nearest" });
}

/** Text offset of the click inside the read view; the superscript numbers do not count. */
function clickOffset() {
  const selection = window.getSelection();
  if (!selection || !selection.rangeCount) return null;
  const { startContainer, startOffset } = selection.getRangeAt(0);
  if (!reader.contains(startContainer)) return null;

  const walker = document.createTreeWalker(reader, NodeFilter.SHOW_TEXT, {
    acceptNode: (node) =>
      node.parentElement.closest(".find-num") ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT,
  });
  let offset = 0;
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (node === startContainer) return offset + startOffset;
    offset += node.length;
  }
  return null;
}

function editAt(offset) {
  // The textarea may contain leading whitespace that was trimmed before the check.
  const lead = Math.max(0, textarea.value.indexOf(checkedText));
  showEditor({ focus: true, caret: offset === null ? textarea.value.length : lead + offset });
}

/** @param {(id: number) => void} onSelect  called when a mark or its number is clicked */
export function initReader(onSelect) {
  reader.addEventListener("click", (event) => {
    const marked = event.target.closest("[data-id]");
    if (marked) onSelect(Number(marked.dataset.id));
    else editAt(clickOffset());
  });

  reader.addEventListener("keydown", (event) => {
    if (event.key !== "Enter") return;
    event.preventDefault();
    if (event.metaKey || event.ctrlKey) textarea.form.requestSubmit();
    else editAt(null);
  });
}
