// Builds a site adapter (same shape as `chatgpt` in chatgpt.js, see SiteAdapter there) from a few CSS selectors.
// Used by the adapters for the other AI chats (Copilot, Gemini, Claude); chatgpt.js keeps its own code.
//
// A send is recognised by Enter in the composer (index.js), a click on a known send button or a submit button
// in the composer's form, or the submit of that form.

/**
 * @typedef {object} SiteSelectors
 * @property {string[]} composer     prompt inputs, most specific first (textarea or contenteditable)
 * @property {string[]} sendButton   buttons that send the prompt
 * @property {string[]} notSend      buttons that must never count as send (e.g. "Stop")
 */

function isVisible(element) {
  return element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden';
}

/** Replaces the whole contents of a contenteditable editor the way a user paste would. */
function replaceEditableText(editor, text) {
  editor.focus();
  const range = document.createRange();
  range.selectNodeContents(editor);
  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);

  // Rich editors (ProseMirror, Tiptap, Quill) turn a pasted plain text into paragraphs; execCommand is the fallback.
  const data = new DataTransfer();
  data.setData('text/plain', text);
  const paste = new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true });
  editor.dispatchEvent(paste);
  if (!paste.defaultPrevented) document.execCommand('insertText', false, text);
}

/** Sets a textarea's value so that frameworks (React etc.) notice the change. */
function replaceTextareaValue(textarea, text) {
  textarea.focus();
  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
  setter.call(textarea, text);
  textarea.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertReplacementText', data: text }));
  textarea.dispatchEvent(new Event('change', { bubbles: true }));
}

/**
 * @param {SiteSelectors} selectors
 * @returns {import('./chatgpt.js').SiteAdapter}
 */
export function makeAdapter({ composer, sendButton, notSend }) {
  const COMPOSER = composer.join(', ');
  const SEND = sendButton.join(', ');
  const NOT_SEND = notSend.join(', ');

  /** @returns {HTMLElement | null} */
  function getComposer() {
    /** @type {HTMLElement[]} */
    const candidates = [...document.querySelectorAll(COMPOSER)];
    return candidates.find(isVisible) ?? candidates[0] ?? null;
  }

  /** @returns {HTMLButtonElement | null} */
  function findSendButton() {
    const form = getComposer()?.closest('form');
    /** @type {HTMLButtonElement[]} */
    const buttons = [
      ...document.querySelectorAll(SEND),
      ...(form ? form.querySelectorAll('button[type="submit"]') : []),
    ];
    return buttons.find((b) => !b.matches(NOT_SEND) && !b.disabled && isVisible(b)) ?? null;
  }

  return {
    getComposer,

    isSendButton(target) {
      if (!(target instanceof Element)) return false;
      const button = target.closest('button');
      if (!button || button.matches(NOT_SEND)) return false;
      if (button.matches(SEND)) return true;
      const form = getComposer()?.closest('form');
      return button.type === 'submit' && form != null && button.form === form;
    },

    isComposerForm(form) {
      const input = getComposer();
      return input != null && form.contains(input);
    },

    getPrompt() {
      const input = getComposer();
      if (!input) return '';
      const text = input instanceof HTMLTextAreaElement ? input.value : input.innerText;
      return text.replace(/\n{3,}/g, '\n\n').trim();
    },

    setPrompt(text) {
      const input = getComposer();
      if (!input) return;
      if (input instanceof HTMLTextAreaElement) replaceTextareaValue(input, text);
      else replaceEditableText(input, text);
    },

    send() {
      const button = findSendButton();
      if (button) {
        button.click();
        return;
      }
      const form = getComposer()?.closest('form');
      if (form) {
        form.requestSubmit();
        return;
      }
      // Neither button nor form found: press Enter in the editor instead.
      getComposer()?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true, cancelable: true }),
      );
    },
  };
}
