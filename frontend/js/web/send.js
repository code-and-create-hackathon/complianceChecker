// "Send" buttons (data-send="prompt" | "suggestion"). Stage 1 has no direct chat integration:
// the text is copied to the clipboard and the target chat (SEND_TARGET) is opened in a new tab.
import { SEND_TARGET } from "../shared/config.js";
import { $, announce } from "./dom.js";
import { copyText, textFor } from "./copy.js";

const sendNote = $("send-note");

export function initSend() {
  $("send-target-name").textContent = SEND_TARGET.name;
  document.querySelectorAll('[data-send="prompt"]').forEach((button) => {
    button.textContent = `Send to ${SEND_TARGET.name}`;
  });

  $("result").addEventListener("click", async (event) => {
    const button = event.target.closest("[data-send]");
    if (!button) return;

    const copied = await copyText(textFor(button, "send"));
    window.open(SEND_TARGET.url, "_blank", "noopener");

    sendNote.textContent = copied
      ? `Copied. Paste it into ${SEND_TARGET.name} (Ctrl/⌘ + V).`
      : `Copying failed. Please copy the text manually and paste it into ${SEND_TARGET.name}.`;
    sendNote.hidden = false;
    announce(sendNote.textContent);
  });
}
