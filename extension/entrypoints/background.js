// Service worker: will run the compliance check (requestLlm from @shared) for the content scripts.
export default defineBackground(() => {
  console.log('[compliance] background started', { id: browser.runtime.id });

  // Toolbar icon → rules page.
  browser.action.onClicked.addListener(() => browser.runtime.openOptionsPage());
});
