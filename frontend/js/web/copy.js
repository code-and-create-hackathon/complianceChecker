// Copy buttons in the result panel: data-copy="prompt" copies the prompt,
// data-copy="suggestion" the sanitised version.
import { $, announce } from "./dom.js";
import { state } from "./state.js";
import { getSuggestion } from "./result_view.js";

const COPY_FEEDBACK_MS = 1600;

async function copyText(text) {
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

export function initCopy() {
  $("result").addEventListener("click", async (event) => {
    const button = event.target.closest("[data-copy]");
    if (!button) return;

    const label = button.querySelector(".button-label");
    label.dataset.original ??= label.textContent;

    // The prompt copy is the checked prompt, not whatever the field contains now.
    const text =
      button.dataset.copy === "suggestion" ? getSuggestion() : (state.result?.request.prompt ?? $("prompt").value);
    const ok = await copyText(text);
    label.textContent = ok ? "Copied" : "Copy failed";
    announce(label.textContent);
    setTimeout(() => {
      label.textContent = label.dataset.original;
    }, COPY_FEEDBACK_MS);
  });
}
