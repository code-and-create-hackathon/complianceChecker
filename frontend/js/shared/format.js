// Number and text formatting shared by website and extension. Pure functions, no DOM access.

export const LOCALE = "en-GB";

export const numberFormat = new Intl.NumberFormat(LOCALE);

/** "1 rule" / "3 rules" */
export function plural(count, one, many) {
  return `${numberFormat.format(count)} ${count === 1 ? one : many}`;
}

/** "512 B" / "3.4 KB" */
export function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toLocaleString(LOCALE, { maximumFractionDigits: 1 })} KB`;
}
