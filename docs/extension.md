# Chrome extension

Manifest V3, built with [WXT](https://wxt.dev). Build and load: [setup.md](setup.md#4-extension).

## Behaviour

- On ChatGPT, Gemini and Claude it holds back every send (Enter or send button) and checks the prompt first.
- Safe → sent. Blocked → dialog with the problems and, if available, a sanitised version.
- Not logged in, session expired or backend unreachable → nothing is sent.
- Login happens on the settings page (toolbar icon). Workers use the published company policy.

## Backend endpoint

`BACKEND_ORIGIN` in `frontend/js/shared/config.js` is the single setting for website and extension:

- The website ignores it and calls `/api` on its own origin.
- The extension calls it directly and derives its host permission from it. Rebuild after changing it.
- `""` = demo mode: local mock check; nothing leaves the browser. Rules must be uploaded on the settings page but
  are not evaluated: only the few patterns of `frontend/js/shared/mock_llm.js` are found, see
  [backend.md](backend.md#mock-provider).

## Code

| Path                          | Purpose                                         |
|-------------------------------|-------------------------------------------------|
| `entrypoints/content/`        | One adapter per chat site, the blocking dialog  |
| `entrypoints/background.js`   | Calls the backend, opens the settings page      |
| `entrypoints/options/`        | Settings page: login, rules                     |
| `utils/`                      | Storage and message helpers                     |

Shared code comes from `frontend/js/shared/` via the `@shared` alias. Keep that folder free of `window`/DOM access.
