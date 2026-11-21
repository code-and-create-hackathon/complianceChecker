// Small DOM helpers shared by the website modules. Pure formatting lives in ../shared/format.js.

export const $ = (id) => document.getElementById(id);

/** Message for screen readers (aria-live). */
export function announce(message) {
  const srStatus = $("sr-status");
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
