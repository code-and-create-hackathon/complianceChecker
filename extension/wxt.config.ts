import { defineConfig } from 'wxt';
// The endpoint is defined only in the shared config; the manifest just follows it.
import { API_URL } from '../frontend/js/shared/config.js';

// See https://wxt.dev/api/reference/wxt/interfaces/inlineconfig.html
export default defineConfig({
  manifest: {
    name: 'Compliance Checker',
    description: 'Checks prompts against internal compliance rules before they are sent to an AI chat.',
    permissions: ['storage'],
    // No popup: clicking the toolbar icon opens the rules (options) page, see background.js.
    action: { default_title: 'Compliance Checker – rules' },
    // Lets the service worker call the backend without CORS headers on the server.
    // Empty API_URL → demo mode, no network access needed.
    host_permissions: API_URL ? [`${new URL(API_URL).origin}/*`] : [],
  },
  // Shared code with the website lives in ../frontend/js/shared (import via '@shared/...').
  alias: {
    '@shared': '../frontend/js/shared',
  },
  vite: () => ({
    server: { fs: { allow: ['..'] } },
  }),
});
