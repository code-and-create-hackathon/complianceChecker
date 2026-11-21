// Site adapter for Claude (claude.ai): a ProseMirror (Tiptap) editor with a "Send message" button.
import { makeAdapter } from './make_adapter.js';

export const claude = makeAdapter({
  composer: ['div.ProseMirror[contenteditable="true"]', 'fieldset [contenteditable="true"]'],
  sendButton: ['button[aria-label*="Send" i]'],
  notSend: ['[aria-label*="Stop" i]'],
});
