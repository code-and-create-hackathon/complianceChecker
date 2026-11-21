// Modal overlay on the chat page that shows the check status and result.
// Lives in a shadow root so the chat site's CSS cannot break it (and ours cannot break the site).
// All texts are set via textContent: problems and prompts are never parsed as HTML.
import css from './dialog.css?inline';

const SEVERITY_LABEL = { critical: 'Critical', high: 'High', medium: 'Medium', low: 'Notice' };

/**
 * @typedef {{label: string, primary?: boolean, onClick: () => void}} Action
 * @typedef {object} View
 * @property {'busy' | 'blocked' | 'notice' | 'error'} tone
 * @property {string} title
 * @property {string} [text]
 * @property {import('@shared/validate.js').Problem[]} [problems]
 * @property {string} [improved]          suggested prompt, shown read-only
 * @property {Action[]} [actions]
 * @property {string} [footer]
 */

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function renderProblem(problem) {
  const item = el('li', `problem severity-${problem.severity}`);
  const head = el('div', 'problem-head');
  head.append(el('span', 'badge', SEVERITY_LABEL[problem.severity] ?? problem.severity), el('span', 'rule', problem.ruleId));
  item.append(head, el('div', 'problem-message', problem.message));
  if (problem.location) item.append(el('q', 'problem-location', problem.location));
  return item;
}

/** @param {{onDismiss: () => void}} options  called when the user closes the dialog (Esc, backdrop) */
export function createDialog({ onDismiss }) {
  /** @type {HTMLElement | null} */
  let host = null;
  let root;
  let dismissible = false;

  function ensureMounted() {
    if (host?.isConnected) return;
    host = el('div');
    host.id = 'compliance-checker-dialog';
    root = host.attachShadow({ mode: 'open' });
    document.documentElement.append(host);
  }

  function close() {
    host?.remove();
    host = null;
  }

  function dismiss() {
    if (!dismissible) return;
    close();
    onDismiss();
  }

  /** @param {View} view */
  function show(view) {
    ensureMounted();
    dismissible = view.tone !== 'busy';

    const style = el('style', '', css);
    const backdrop = el('div', 'backdrop');
    backdrop.addEventListener('click', (event) => {
      if (event.target === backdrop) dismiss();
    });

    const panel = el('div', `panel tone-${view.tone}`);
    panel.setAttribute('role', view.tone === 'busy' ? 'status' : 'alertdialog');
    panel.setAttribute('aria-modal', 'true');
    panel.setAttribute('aria-labelledby', 'cc-title');
    panel.tabIndex = -1;
    panel.addEventListener('keydown', (event) => {
      event.stopPropagation(); // keep the chat site's shortcuts out of the dialog
      if (event.key === 'Escape') dismiss();
    });

    const header = el('div', 'header');
    if (view.tone === 'busy') header.append(el('span', 'spinner'));
    const title = el('h2', 'title', view.title);
    title.id = 'cc-title';
    header.append(title);
    panel.append(header);

    if (view.text) panel.append(el('p', 'text', view.text));

    if (view.problems?.length) {
      const list = el('ul', 'problems');
      list.append(...view.problems.map(renderProblem));
      panel.append(list);
    }

    if (view.improved) {
      panel.append(el('div', 'label', 'Suggested compliant version'), el('pre', 'improved', view.improved));
    }

    const primary = [];
    if (view.actions?.length) {
      const actions = el('div', 'actions');
      for (const action of view.actions) {
        const button = el('button', action.primary ? 'button primary' : 'button', action.label);
        button.type = 'button';
        button.addEventListener('click', action.onClick);
        actions.append(button);
        if (action.primary) primary.push(button);
      }
      panel.append(actions);
    }

    if (view.footer) panel.append(el('p', 'footer', view.footer));

    backdrop.append(panel);
    root.replaceChildren(style, backdrop);
    (primary[0] ?? panel).focus();
  }

  return { show, close };
}
