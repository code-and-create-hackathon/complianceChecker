# Local setup

Backend with real Bedrock checks, website for the administrator, extension for the worker.
Requires JDK 21+ (Maven comes with `./mvnw`) and Node.js for the extension.

## 1. AWS credentials

A profile in `~/.aws/credentials` (or `aws sso login`) that may call `bedrock:InvokeModel` for Nova 2 Lite in the
EU regions, see [backend.md](backend.md#amazon-bedrock). Skip this for the offline demo.

## 2. Backend

```bash
cd backend
ADMIN_CODE=choose-a-code AWS_PROFILE=<profile> ./mvnw spring-boot:run
```

- Without `ADMIN_CODE` the administrator cannot log in.
- Without AWS: replace `AWS_PROFILE=…` with `LLM_PROVIDER=mock` (simulated results).
- Tests: `./mvnw test`

The backend also serves the website on http://localhost:8080.

## 3. Website

1. Log in as `admin` with the admin code.
2. Upload `Testdata/compliance-guide-musterfinanz.md` → **Publish as company policy**.
3. Under **Team**, add a worker, e.g. `erika` (logs in without a password).

Without a backend: serve `frontend/` statically (e.g. `python3 -m http.server`) and open it with `?mock=1`.
This browser-only check knows far fewer patterns than the backend mock (it lets test prompts T5–T8 through), see
[backend.md](backend.md#mock-provider).

## 4. Extension

`BACKEND_ORIGIN` in `frontend/js/shared/config.js` must point to the backend (default `http://localhost:8080`).

```bash
cd extension
npm install
npm run build   # load extension/.output/chrome-mv3 via chrome://extensions → Load unpacked
npm run dev     # or: own browser with hot reload
```

`npm run dev` only finds Google Chrome. For another Chromium browser, create the git-ignored
`extension/web-ext.config.ts`:

```ts
import { defineWebExtConfig } from 'wxt';

export default defineWebExtConfig({
  binaries: { chrome: '/usr/bin/chromium-browser' },
  chromiumArgs: ['--user-data-dir=./.wxt/chrome-data'], // keeps logins between runs
});
```

## 5. Try it

1. Click the extension's toolbar icon, log in as `erika`.
2. On chatgpt.com, gemini.google.com or claude.ai send `Pay DE89 3704 0044 0532 0130 00` → blocked, suggests `[IBAN_1]`.
3. `Hello` is sent normally. More cases: [Testdata/test-prompts.md](../Testdata/test-prompts.md).

## Troubleshooting

| Symptom                                    | Fix                                                       |
|--------------------------------------------|-----------------------------------------------------------|
| "Not logged in" after a backend restart    | Sessions live in memory: log in again                     |
| "AWS credentials are missing or expired"   | `aws sso login` or check `AWS_PROFILE`, restart backend   |
| "No compliance policy has been published"  | Administrator publishes one (step 3)                      |
| Changed `BACKEND_ORIGIN`                   | Rebuild the extension and reload it in `chrome://extensions` |
