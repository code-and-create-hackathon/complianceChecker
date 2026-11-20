// Website entry point (Stage 1).
import { requestLlm } from "../shared/request_llm.js";

function init() {
  const form = document.getElementById("check-form");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const prompt = form.elements.prompt.value;
    const compliance = await form.elements.compliance.files[0].text();

    const result = await requestLlm(prompt, compliance);
    console.log(result); // TODO: render result
  });
}

init();
