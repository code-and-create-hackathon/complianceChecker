// Content script on AI chat sites: will intercept "send" and gate it on the compliance check.
// Start with one site; more providers follow via a per-site adapter table.
export default defineContentScript({
  matches: ['https://chatgpt.com/*'],
  main() {
    console.log('[compliance] content script loaded on', location.hostname);
  },
});
