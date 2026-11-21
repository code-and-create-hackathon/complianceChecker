// Site adapter for chatgpt.com. Every supported chat site gets one adapter with the same shape,
// so index.js stays site-independent.
//
// ChatGPT ships different composers (and changes them often), so nothing here relies on a single id:
// - ProseMirror editor: <div id="prompt-textarea" contenteditable>, send button data-testid="send-button"
// - plain composer:     <form method="post"><textarea name="prompt"> … <button type="submit" aria-label="Send message">
// A send is recognised by Enter in the composer, a click on its form's submit button, or the form's submit.

/**
 * @typedef {object} SiteAdapter
 * @property {() => HTMLElement | null} getComposer   the visible prompt input
 * @property {(target: EventTarget | null) => boolean} isSendButton
 * @property {(form: HTMLFormElement) => boolean} isComposerForm
 * @property {() => string} getPrompt
 * @property {(text: string) => void} setPrompt
 * @property {() => void} send               submits the current prompt (as the user would)
 */

const COMPOSER_CANDIDATES = [
  '#prompt-textarea',
  'textarea[name="prompt"]',
  'form [contenteditable="true"]',
  'form textarea',
].join(', ');

const KNOWN_SEND_BUTTONS = 'button[data-testid="send-button"], button#composer-submit-button';
const NOT_SEND = '[data-testid="stop-button"], [aria-label*="Stop" i]';

function isVisible(element) {
  return element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden';
}

/** @returns {HTMLElement | null} */
function getComposer() {
  /** @type {HTMLElement[]} */
  const candidates = [...document.querySelectorAll(COMPOSER_CANDIDATES)];
  return candidates.find(isVisible) ?? candidates[0] ?? null;
}

/** @returns {HTMLButtonElement | null} */
function findSendButton() {
  const composer = getComposer();
  const scope = composer?.closest('form') ?? document;
  /** @type {HTMLButtonElement[]} */
  const buttons = [...scope.querySelectorAll(`${KNOWN_SEND_BUTTONS}, button[type="submit"]`)];
  return buttons.find((b) => !b.matches(NOT_SEND) && !b.disabled && isVisible(b)) ?? null;
}

/** Replaces the whole contents of a contenteditable editor the way a user paste would. */
function replaceEditableText(editor, text) {
  editor.focus();
  const range = document.createRange();
  range.selectNodeContents(editor);
  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);

  // ProseMirror turns a pasted plain text into proper paragraphs; execCommand is the fallback.
  const data = new DataTransfer();
  data.setData('text/plain', text);
  const paste = new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true });
  editor.dispatchEvent(paste);
  if (!paste.defaultPrevented) document.execCommand('insertText', false, text);
}

/** @type {SiteAdapter} */
export const chatgpt = {
  getComposer,

  isSendButton(target) {
    if (!(target instanceof Element)) return false;
    const button = target.closest('button');
    if (!button || button.matches(NOT_SEND)) return false;
    if (button.matches(KNOWN_SEND_BUTTONS)) return true;
    // A submit button in the composer's form (the other buttons there are type="button").
    const form = getComposer()?.closest('form');
    return button.type === 'submit' && form != null && button.form === form;
  },

  isComposerForm(form) {
    const composer = getComposer();
    return composer != null && form.contains(composer);
  },

  getPrompt() {
    const composer = getComposer();
    if (!composer) return '';
    const text = composer instanceof HTMLTextAreaElement ? composer.value : composer.innerText;
    return text.replace(/\n{3,}/g, '\n\n').trim();
  },

  setPrompt(text) {
    const composer = getComposer();
    if (!composer) return;
    if (composer instanceof HTMLTextAreaElement) {
      // Frameworks track the value themselves: use the native setter, then announce the change.
      composer.focus();
      const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
      setter.call(composer, text);
      composer.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertReplacementText', data: text }));
      composer.dispatchEvent(new Event('change', { bubbles: true }));
    } else {
      replaceEditableText(composer, text);
    }
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
