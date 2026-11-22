# API definition

`POST /api/check`, JSON → JSON. Validator: [`validate.js`](frontend/js/shared/validate.js) (`validateRequest` / `validateResponse` return the payload or throw a `ValidationError`). Other implementations must follow the same rules.

The client must be logged in (see [Accounts](#accounts)) and sends `Authorization: Bearer <token>`. The prompt is checked against the **company policy the administrator published**. Clients cannot send their own rules.

## Request

```json
{ "prompt": "Pay DE89 3704 0044 0532 0130 00" }
```

| Field    | Type   | Rules                     |
|----------|--------|---------------------------|
| `prompt` | string | non-empty, ≤ 20 000 chars |

Invalid request → HTTP `400`. Not logged in → `401`. No policy published yet → `409` (`NO_POLICY`).

"chars" means UTF-16 code units, not bytes or code points: most characters count as 1, an emoji or other character outside the BMP counts as 2 (JS `s.length`, Java `String.length()`). This is what the backend's validation counts.

## Response

```json
{
  "isSafe": false,
  "problems": [
    {
      "ruleId": "R-02",
      "category": "Account data",
      "severity": "critical",
      "message": "Contains an IBAN.",
      "location": "DE89 3704 0044 0532 0130 00"
    }
  ],
  "improvedVersion": "Pay [IBAN_1]"
}
```

| Field             | Type      | Rules                                                              |
|-------------------|-----------|--------------------------------------------------------------------|
| `isSafe`          | boolean   | `true` ⇔ no problem has severity `critical`, `high` or `medium`    |
| `problems`        | object[]  | one entry per match, see below; `[]` = nothing found               |
| `improvedVersion` | string    | sanitised prompt with placeholders like `[IBAN_1]`; `""` = no suggestion |

### `problems[]`

| Field      | Type   | Rules                                                                 |
|------------|--------|-----------------------------------------------------------------------|
| `ruleId`   | string | non-empty, rule from the compliance file, e.g. `R-02`                 |
| `category` | string | type of violation from the compliance file or derived from the rule, e.g. `Customer data`; never a value from the prompt; `""` = unknown. Optional for clients (the browser demo mode has none) |
| `severity` | string | `critical` \| `high` \| `medium` \| `low` (as in the compliance file) |
| `message`  | string | non-empty, explanation for the user                                   |
| `location` | string | the matched passage, copied **verbatim** from `prompt`; `""` = no specific passage |

- The same violation in two places → two entries.
- `low` is a notice: the prompt may still be sent (`isSafe` stays `true`).
- `location` is text, not a position: the client finds it in the prompt itself (identical passages are matched in order). A `location` that does not occur in the prompt is listed but not marked.

### Incomplete check

Nova must return the complete prompt when it finds no problem. If it reports no problem but changed the prompt, the check is repeated once with a stricter instruction. If that answer is inconsistent again, the check is incomplete: status `200` with this body. The prompt is **not** approved; `improvedVersion` is Nova's unchecked output.

```json
{
  "verification": "incomplete",
  "isSafe": false,
  "problems": [],
  "improvedVersion": "Please summarise.",
  "message": "Nova returned an incomplete or contradictory answer. The prompt is not approved.",
  "requestId": "6f1c..."
}
```

## Accounts

> **Demo authentication.** TODO(security): workers log in with their username only, without a password. Replace with the company's identity provider (e.g. Amazon Cognito / SSO) before real use.

- **Roles:** one `admin` (created at startup from `ADMIN_USERNAME`; logs in with the extra `ADMIN_CODE`) and any number of `worker`s.
- **No self-registration:** only the administrator creates workers.
- **Tokens:** login returns a random token, valid for 12 h and kept in the backend's memory. A backend restart logs everyone out.
- **Deny by default:** every `/api` endpoint except login, QR demo login and Nova status requires the token. Without one → `401 UNAUTHORIZED`; a worker calling an admin endpoint → `403 FORBIDDEN`.

| Method | Path               | Who      | Body → Answer |
|--------|--------------------|----------|---------------|
| `POST` | `/api/auth/login`  | everyone | `{"username", "adminCode"?}` → `{"token", "username", "role"}`; `401 LOGIN_FAILED` for an unknown user or a wrong/missing admin code |
| `POST` | `/api/auth/logout` | logged in | → `204`, the token is invalid afterwards |
| `GET`  | `/api/auth/me`     | logged in | → `{"username", "role", "createdAt"}` |
| `GET`  | `/api/policy`      | logged in | → `{"version", "text", "publishedAt", "publishedBy"}`; `409 NO_POLICY` if none is published |
| `PUT`  | `/api/policy`      | admin    | `{"text"}` (non-empty, ≤ 500 000 chars) → the new version |
| `GET`  | `/api/users`       | admin    | → `[{"username", "role", "createdAt"}]` |
| `POST` | `/api/users`       | admin    | `{"username"}` → `201` with the new **worker**; `409 USERNAME_TAKEN`; usernames are 2–40 chars of `a-z 0-9 . _ -` |
| `POST` | `/api/auth/demo-key`   | admin    | → `{"key", "expiresAt"}`: QR demo key (`frontend/qr.html`), valid 3 minutes |
| `POST` | `/api/auth/demo-login` | everyone | `{"key"}` → like login, as the account `guest`; the session ends after 3 minutes; `401 DEMO_KEY_EXPIRED` for an unknown or expired key. `guest` cannot use the normal login |
| `GET`  | `/api/nova/status` | everyone | Nova console mode only: → `{"connection", "lastOutcome", "lastLatencyMs"}` for the status badge |
| `GET`  | `/api/prompts`     | admin    | → the last 100 prompts sent in Nova console mode, **original text** |

`role` is `"admin"` or `"worker"`. Policy versions are never changed or deleted: publishing creates the next version, and the newest is the active one.

Policy text: multiple compliance files are concatenated, each prefixed with `<!-- file: name.md -->`, separated by `\n\n---\n\n`.

## General

- All fields required; no `null`; no extra fields.
- The web client aborts a request after 60 s (`REQUEST_TIMEOUT_MS` in `frontend/js/shared/config.js`); the backend should answer within that time.

## Error responses

Every error response (status `4xx` or `5xx`) has this body. All texts are fixed server-side wording and never contain parts of the request.

```json
{
  "error": "INVALID_REQUEST",
  "message": "The request is invalid. See fieldErrors.",
  "fieldErrors": { "prompt": "must not be empty" },
  "requestId": "6f1c..."
}
```

| Status | `error`                  | When                                       |
|--------|--------------------------|--------------------------------------------|
| 400    | `INVALID_REQUEST`        | empty/missing/too long field, invalid JSON |
| 401    | `UNAUTHORIZED`           | no or invalid/expired login token          |
| 401    | `LOGIN_FAILED`           | unknown user or wrong admin code           |
| 403    | `FORBIDDEN`              | a worker called an administrator endpoint  |
| 409    | `NO_POLICY`              | check before any policy was published      |
| 409    | `USERNAME_TAKEN`         | the user already exists                    |
| 405    | `METHOD_NOT_ALLOWED`     | wrong HTTP method                          |
| 415    | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` is not `application/json`   |
| 502    | `LLM_ACCESS_DENIED`      | AWS refused access to Bedrock (401/403)    |
| 502    | `LLM_AWS_ERROR`          | AWS rejected the call (other AWS error)    |
| 502    | `LLM_CONNECTION_ERROR`   | AWS not reachable or credentials missing   |
| 502    | `LLM_NOT_CONNECTED`      | Nova console mode: no credentials entered yet |
| 502    | `LLM_INVALID_RESPONSE`   | the model returned no usable result (after one retry) |
| 502    | `LLM_UNAVAILABLE`        | any other LLM failure                      |
| 500    | `INTERNAL_ERROR`         | anything else                              |

Other framework errors use the name of their HTTP status as `error`, e.g. `404` → `NOT_FOUND`.

| Field         | Type   | Rules                                                                 |
|---------------|--------|-----------------------------------------------------------------------|
| `error`       | string | stable machine-readable code, see the table above                     |
| `message`     | string | human-readable, safe to show to the user (e.g. which AWS problem to fix for `502`) |
| `fieldErrors` | object | field name → problem; `{}` if not about a field                       |
| `requestId`   | string | id to find the request in the server log                              |

Clients show `message` together with the status; a body that is not JSON is treated as a generic error.
