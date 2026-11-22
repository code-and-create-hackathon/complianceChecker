// Content script on AI chat sites: holds back every "send", has the prompt checked against the
// compliance rules (via the background service worker) and only lets it through when it is safe.
// Fails closed: if the check cannot run, the prompt is not sent.
import { chatgpt } from './chatgpt.js';
import { gemini } from './gemini.js';
import { claude } from './claude.js';
import { createDialog } from './dialog.js';
import { sendCheck, sendOpenRules } from '@/utils/messages.js';

// One adapter per site; more providers go here (and into `matches`).
const ADAPTERS = {
  'chatgpt.com': chatgpt,
  'gemini.google.com': gemini,
  'claude.ai': claude,
};
// globalThis: the build also imports this file (outside a browser) to read `matches`.
const site = ADAPTERS[globalThis.location?.hostname] ?? chatgpt;

const DEMO_NOTE = 'Demo mode: checked locally with sample rules, no backend connected.';

export default defineContentScript({
  matches: ['https://chatgpt.com/*', 'https://gemini.google.com/*', 'https://claude.ai/*'],
  // Register our listeners before the site's own scripts run, so we always see a send first.
  runAt: 'document_start',

  main() {
    console.log('[compliance] content script loaded on', location.hostname);
    document.documentElement.dataset.complianceChecker = 'active'; // handy for debugging

    let busy = false; // a check is running or its result is on screen
    let bypass = false; // true only while we re-trigger the send ourselves

    const dialog = createDialog({ onDismiss: () => { busy = false; } });

    function finish() {
      dialog.close();
      busy = false;
      site.getComposer()?.focus();
    }

    /** Sends the checked prompt — unless it was changed after the check, then it is checked again. */
    function sendChecked(checkedPrompt) {
      if (site.getPrompt() !== checkedPrompt) {
        gate();
        return;
      }
      dialog.close();
      busy = false;
      bypass = true;
      try {
        site.send();
      } finally {
        bypass = false;
      }
    }

    function useImproved(text) {
      site.setPrompt(text);
      finish();
    }

    async function gate() {
      const prompt = site.getPrompt();
      if (!prompt) {
        // Nothing to check (e.g. only an attachment): let the site handle it.
        sendChecked(prompt);
        return;
      }

      busy = true;
      dialog.show({ tone: 'busy', title: 'Checking prompt against compliance rules…' });

      const reply = await sendCheck(prompt);
      const footer = reply.mock ? DEMO_NOTE : undefined;

      if (!reply.ok && reply.error === 'noRules') {
        dialog.show({
          tone: 'error',
          title: 'No compliance rules loaded',
          text: 'Prompts cannot be checked until you upload at least one rules file. The prompt was not sent.',
          actions: [
            { label: 'Cancel', onClick: finish },
            {
              label: 'Open rules page',
              primary: true,
              onClick: () => {
                sendOpenRules();
                finish();
              },
            },
          ],
          footer,
        });
        return;
      }

      if (!reply.ok && reply.error === 'notLoggedIn') {
        dialog.show({
          tone: 'error',
          title: 'Not logged in',
          text: 'Log in to Four Eyes before sending prompts. The prompt was not sent.',
          actions: [
            { label: 'Cancel', onClick: finish },
            {
              label: 'Open login page',
              primary: true,
              onClick: () => {
                sendOpenRules();
                finish();
              },
            },
          ],
        });
        return;
      }

      if (!reply.ok) {
        dialog.show({
          tone: 'error',
          title: 'Check failed — prompt not sent',
          text: reply.message,
          actions: [
            { label: 'Cancel', onClick: finish },
            { label: 'Try again', primary: true, onClick: gate },
          ],
          footer,
        });
        return;
      }

      const { isSafe, problems, improvedVersion } = reply.result;

      if (isSafe && problems.length === 0) {
        sendChecked(prompt);
        return;
      }

      const improvedAction = improvedVersion
        ? [{ label: 'Use suggested version', primary: !isSafe, onClick: () => useImproved(improvedVersion) }]
        : [];

      if (isSafe) {
        // Only notices (severity "low"): the user may send as is.
        dialog.show({
          tone: 'notice',
          title: 'Prompt is compliant, with notices',
          problems,
          improved: improvedVersion,
          actions: [
            { label: 'Edit prompt', onClick: finish },
            ...improvedAction,
            { label: 'Send anyway', primary: true, onClick: () => sendChecked(prompt) },
          ],
          footer,
        });
        return;
      }

      dialog.show({
        tone: 'blocked',
        title: 'Prompt blocked',
        text: improvedVersion
          ? 'This prompt breaks your compliance rules and was not sent. Edit it or use the suggested version, then send again.'
          : 'This prompt breaks your compliance rules and was not sent. Please edit it and send again.',
        problems,
        improved: improvedVersion,
        actions: [{ label: 'Edit prompt', primary: !improvedVersion, onClick: finish }, ...improvedAction],
        footer,
      });
    }

    /** Stops the site from seeing the event at all (we listen on window in the capture phase, i.e. first). */
    function holdBack(event) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }

    /** Holds back a send attempt and starts the check. Empty prompts are left to the site. */
    function intercept(event) {
      if (bypass) return;
      if (!busy && !site.getPrompt()) return;
      holdBack(event);
      if (!busy) gate();
    }

    window.addEventListener(
      'keydown',
      (event) => {
        if (event.key !== 'Enter' || event.shiftKey || event.altKey || event.isComposing) return;
        const composer = site.getComposer();
        if (!composer || !(event.target instanceof Node) || !composer.contains(event.target)) return;
        intercept(event);
      },
      true,
    );

    // Some sites send on pointerdown/mousedown instead of click: hold all three back for the send button.
    for (const type of ['pointerdown', 'mousedown']) {
      window.addEventListener(
        type,
        (event) => {
          if (!bypass && site.isSendButton(event.target) && (busy || site.getPrompt())) holdBack(event);
        },
        true,
      );
    }
    window.addEventListener(
      'click',
      (event) => {
        if (site.isSendButton(event.target)) intercept(event);
      },
      true,
    );

    // Last line of defence: the composer's form is submitted (e.g. implicit submission, native POST).
    window.addEventListener(
      'submit',
      (event) => {
        if (event.target instanceof HTMLFormElement && site.isComposerForm(event.target)) intercept(event);
      },
      true,
    );
  },
});
