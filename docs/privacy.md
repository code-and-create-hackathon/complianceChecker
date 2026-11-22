# Privacy and limitations

## Data handling

- **Prompts are never stored or logged.** Logs hold metadata only: lengths, result, rule ids, severities, model,
  duration, AWS error type.
- **Bedrock sees placeholders** for everything the pattern scanner finds. Names without a form of address are not
  found and are sent; the model is told to check for them.
- **`problems[].location`** goes only to the sender of the prompt, never to logs or the database.
- **History stores no text:** time, username, `isSafe`, rule ids and severities. Non-id rule values become `UNSPECIFIED`.
  Databases from older versions may contain a `check_history` table with sanitised prompts: delete `backend/data/`.
- Workers see only their own history; the admin sees everyone's.
- Error responses use fixed texts, never parts of the request or the AWS error.

## Known limitations (demo only, no real data)

Marked `TODO(security)` in the code.

- **No worker passwords;** the admin is protected by one shared `ADMIN_CODE` without rate limiting.
  Next step: SSO (e.g. Amazon Cognito); tokens and role checks stay.
- **Sessions in memory:** a restart logs everyone out; multiple instances do not share them.
- **Tokens in the browser:** `sessionStorage` (website), `chrome.storage.local` (extension).
- **Only gated prompts are checked.** Enforce via force-installed extension and blocking unapproved AI sites.
- **Prompt injection:** scanner findings always count; for other rules the LLM is the only judge.
- One company, one administrator.
