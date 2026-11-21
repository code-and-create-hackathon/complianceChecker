// Site adapter for Google Gemini (gemini.google.com): a Quill editor inside <rich-textarea>.
import { makeAdapter } from './make_adapter.js';

export const gemini = makeAdapter({
  composer: ['rich-textarea .ql-editor[contenteditable="true"]', '.ql-editor[contenteditable="true"]'],
  sendButton: ['button.send-button', 'button[aria-label*="Send" i]'],
  notSend: ['button.stop', '[aria-label*="Stop" i]'],
});
