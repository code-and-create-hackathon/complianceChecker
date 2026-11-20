// Frontend configuration. The API URL is not a secret (it is visible in every browser).
// Demo mode (no backend) is switched via ?mock=1 / ?mock=0, see isMockMode() in request_llm.js.

// TODO: set to the real backend endpoint once it exists.
export const API_URL = "/api/check";

// The check gives up after this time, so the page never hangs on "Checking …".
export const REQUEST_TIMEOUT_MS = 60_000;

// Target chat for "Send". Stage 1 has no direct integration: the text is copied and the chat opened.
export const SEND_TARGET = {
  name: "Microsoft Copilot",
  url: "https://copilot.microsoft.com/",
};
