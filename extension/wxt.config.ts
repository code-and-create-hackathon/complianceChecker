import { defineConfig } from 'wxt';

// See https://wxt.dev/api/reference/wxt/interfaces/inlineconfig.html
export default defineConfig({
  manifest: {
    name: 'Compliance Checker',
    description: 'Checks prompts against internal compliance rules before they are sent to an AI chat.',
    permissions: ['storage'],
    // No popup: clicking the toolbar icon opens the rules (options) page, see background.js.
    action: { default_title: 'Compliance Checker – rules' },
    // TODO: add the AWS backend origin here once it exists, e.g. 'https://api.example.com/*'.
    host_permissions: [],
  },
  // Shared code with the website lives in ../frontend/js/shared (import via '@shared/...').
  alias: {
    '@shared': '../frontend/js/shared',
  },
  vite: () => ({
    server: { fs: { allow: ['..'] } },
  }),
});
