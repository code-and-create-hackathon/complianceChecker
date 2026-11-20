// "Vorschlag übernehmen": puts the improved version from the result into the prompt field.
import { $, announce } from "./dom.js";
import { getSuggestion } from "./result_view.js";
import { setPrompt } from "./prompt_field.js";

export function initSuggestion() {
  $("apply-suggestion").addEventListener("click", () => {
    const suggestion = getSuggestion();
    if (!suggestion) return;
    setPrompt(suggestion);
    announce("Vorschlag übernommen. Bitte erneut prüfen.");
  });
}
