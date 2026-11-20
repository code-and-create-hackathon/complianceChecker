// Small DOM helpers shared by the website modules.

export const $ = (id) => document.getElementById(id);

export const LOCALE = "en-GB";

export const numberFormat = new Intl.NumberFormat(LOCALE);

/** "1 rule" / "3 rules" */
export function plural(count, one, many) {
  return `${numberFormat.format(count)} ${count === 1 ? one : many}`;
}

const srStatus = $("sr-status");

/** Message for screen readers (aria-live). */
export function announce(message) {
  srStatus.textContent = "";
  requestAnimationFrame(() => {
    srStatus.textContent = message;
  });
}

/** Shows an error below a field and marks it invalid ("" = reset). */
export function setFieldError(input, errorEl, message) {
  errorEl.textContent = message;
  errorEl.hidden = !message;
  if (message) input.setAttribute("aria-invalid", "true");
  else input.removeAttribute("aria-invalid");
}
