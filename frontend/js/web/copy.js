// "Copy" buttons (data-copy="prompt" | "suggestion") in the result panel.
import { $, announce } from "./dom.js";
import { getSuggestion } from "./result_view.js";
import { getPrompt } from "./prompt_field.js";

const COPY_FEEDBACK_MS = 1600;

/** Copies text to the clipboard; falls back to execCommand where the Clipboard API is unavailable. */
export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    const helper = document.createElement("textarea");
    helper.value = text;
    helper.setAttribute("readonly", "");
    helper.style.position = "fixed";
    helper.style.opacity = "0";
    document.body.append(helper);
    helper.select();
    const ok = document.execCommand("copy");
    helper.remove();
    return ok;
  }
}

/** Text for a data-copy / data-send button. */
export function textFor(button, attribute) {
  return button.dataset[attribute] === "suggestion" ? getSuggestion() : getPrompt();
}

export function initCopy() {
  $("result").addEventListener("click", async (event) => {
    const button = event.target.closest("[data-copy]");
    if (!button) return;

    const label = button.querySelector(".button-label");
    label.dataset.original ??= label.textContent;

    const ok = await copyText(textFor(button, "copy"));
    label.textContent = ok ? "Copied" : "Copy failed";
    announce(label.textContent);
    setTimeout(() => {
      label.textContent = label.dataset.original;
    }, COPY_FEEDBACK_MS);
  });
}
