// Site adapter for Microsoft Copilot (copilot.microsoft.com, which redirects to copilot.com):
// a plain textarea with a submit button.
import { makeAdapter } from './make_adapter.js';

export const copilot = makeAdapter({
  composer: ['textarea#userInput', 'textarea[data-testid="composer-input"]', 'main textarea'],
  sendButton: ['button[data-testid="submit-button"]', 'button[aria-label*="Submit" i]', 'button[aria-label*="Send" i]'],
  notSend: ['[data-testid="stop-button"]', '[aria-label*="Stop" i]'],
});
