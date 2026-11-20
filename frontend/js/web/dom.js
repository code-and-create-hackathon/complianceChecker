// Small DOM helpers shared by the website modules.

export const $ = (id) => document.getElementById(id);

export const numberFormat = new Intl.NumberFormat("de-DE");

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
