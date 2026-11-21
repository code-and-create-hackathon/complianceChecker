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

"chars" means Unicode characters (code points), not bytes or UTF-16 code units: an emoji counts as 1 (JS `[...s].length`, Python `len(s)`).

## Response

```json
{
  "isSafe": false,
  "problems": [
    {
      "ruleId": "R-02",
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
| `severity` | string | `critical` \| `high` \| `medium` \| `low` (as in the compliance file) |
| `message`  | string | non-empty, explanation for the user                                   |
| `location` | string | the matched passage, copied **verbatim** from `prompt`; `""` = no specific passage |

- The same violation in two places → two entries.
- `low` is a notice: the prompt may still be sent (`isSafe` stays `true`).
- `location` is text, not a position: the client finds it in the prompt itself (identical passages are matched in order). A `location` that does not occur in the prompt is listed but not marked.

## Accounts

> **Demo authentication.** TODO(security): workers log in with their username only, without a password. Replace with the company's identity provider (e.g. Amazon Cognito / SSO) before real use.

- **Roles:** one `admin` (created at startup from `ADMIN_USERNAME`; logs in with the extra `ADMIN_CODE`) and any number of `worker`s.
- **No self-registration:** only the administrator creates workers.
- **Tokens:** login returns a random token, valid for 12 h and kept in the backend's memory. A backend restart logs everyone out.
- **Deny by default:** every `/api` endpoint except login requires the token. Without one → `401 UNAUTHORIZED`; a worker calling an admin endpoint → `403 FORBIDDEN`.

| Method | Path               | Who      | Body → Answer |
|--------|--------------------|----------|---------------|
| `POST` | `/api/auth/login`  | everyone | `{"username", "adminCode"?}` → `{"token", "username", "role"}`; `401 LOGIN_FAILED` for an unknown user or a wrong/missing admin code |
| `POST` | `/api/auth/logout` | logged in | → `204`, the token is invalid afterwards |
| `GET`  | `/api/auth/me`     | logged in | → `{"username", "role", "createdAt"}` |
| `GET`  | `/api/policy`      | logged in | → `{"version", "text", "publishedAt", "publishedBy"}`; `409 NO_POLICY` if none is published |
| `PUT`  | `/api/policy`      | admin    | `{"text"}` (non-empty, ≤ 500 000 chars) → the new version |
| `GET`  | `/api/users`       | admin    | → `[{"username", "role", "createdAt"}]` |
| `POST` | `/api/users`       | admin    | `{"username"}` → `201` with the new **worker**; `409 USERNAME_TAKEN`; usernames are 2–40 chars of `a-z 0-9 . _ -` |

`role` is `"admin"` or `"worker"`. Policy versions are never changed or deleted: publishing creates the next version, and the newest is the active one.

Policy text: multiple compliance files are concatenated, each prefixed with `<!-- file: name.md -->`, separated by `\n\n---\n\n`.

## General

- All fields required; no `null`; no extra fields.
- The web client aborts a request after 60 s (`REQUEST_TIMEOUT_MS` in `frontend/js/shared/config.js`); the backend should answer within that time.
