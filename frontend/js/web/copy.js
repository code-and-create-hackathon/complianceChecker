// "Prompt kopieren" buttons (data-copy) in the result card.
import { $, announce } from "./dom.js";

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

    const ok = await copyText($("prompt").value);
    label.textContent = ok ? "Kopiert" : "Kopieren fehlgeschlagen";
    announce(label.textContent);
    setTimeout(() => {
      label.textContent = label.dataset.original;
    }, COPY_FEEDBACK_MS);
  });
}
