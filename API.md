# API definition

`POST` JSON → JSON. Validator: [`validate.js`](frontend/js/shared/validate.js) (`validateRequest` / `validateResponse` return the payload or throw a `ValidationError`). Other implementations must follow the same rules.

## Request

```json
{ 
 "prompt": "Pay DE89 3704 0044 0532 0130 00",
 "compliance": "# Rules\nNo IBANs." 
}
```

| Field        | Type   | Rules                         |
|--------------|--------|-------------------------------|
| `prompt`     | string | non-empty, ≤ 20 000 chars     |
| `compliance` | string | non-empty, ≤ 500 000 chars    |

Multiple compliance files are concatenated, each prefixed with `<!-- file: name.md -->`, separated by `\n\n---\n\n`. Invalid request → HTTP `400`.

"chars" means Unicode characters (code points), not bytes or UTF-16 code units: an emoji counts as 1 (JS `[...s].length`, Python `len(s)`).

## Response

```json
{ 
  "isSafe": false,
  "problems": ["Contains an IBAN."],
  "improvedVersion": "Pay [IBAN]",
  "categories": ["ACCOUNT_DATA"],
  "simulated": true
}
```

| Field             | Type     | Rules                                  |
|-------------------|----------|----------------------------------------|
| `isSafe`          | boolean  | `true` ⇔ `problems` is empty           |
| `problems`        | string[] | non-empty strings                      |
| `improvedVersion` | string   | `""` = no suggestion                   |
| `categories`      | string[] | **optional**; coarse problem categories: `CUSTOMER_DATA`, `ACCOUNT_DATA`, `INSIDER_INFO`, `CREDENTIALS`, `OTHER`; `[]` if `isSafe` |
| `simulated`       | boolean  | **optional**; `true` if the result comes from the backend's mock mode (`LLM_PROVIDER=mock`, the default). The mock only detects a few patterns (email, phone, credit card, IBAN, confidentiality keywords), ignores the compliance rules and is **not** a real compliance check |

## General

- All fields required except `categories` and `simulated`; no `null`; no other fields.
- The web client aborts a request after 60 s (`REQUEST_TIMEOUT_MS` in `frontend/js/shared/config.js`); the backend should answer within that time.

## `GET /api/history`

The latest 100 checks, newest first (`200 OK`):

```json
[
  {
    "id": 2,
    "createdAt": "2026-11-21T10:00:00Z",
    "isSafe": false,
    "categories": ["CUSTOMER_DATA"],
    "improvedVersion": "Reply to [EMAIL] about her loan."
  }
]
```

Only these fields are stored. The **original prompt, the compliance rules and matched sensitive text are never stored.**
`improvedVersion` is `null` when it could not be stored safely: when it equals the original prompt (e.g. for safe
prompts), still contains detectable sensitive data, or is too long.

## Errors

All backend errors share one shape. Messages are fixed server-side texts and never contain any part of the submitted content.

```json
{
  "error": "INVALID_REQUEST",
  "message": "The request is invalid. See fieldErrors.",
  "fieldErrors": {
    "prompt": "must not be empty"
  },
  "requestId": "6f1c..."
}
```

| Status | `error`                  | When                                       |
|--------|--------------------------|--------------------------------------------|
| 400    | `INVALID_REQUEST`        | empty/missing/too long field, invalid JSON |
| 405    | `METHOD_NOT_ALLOWED`     | wrong HTTP method                          |
| 415    | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` is not `application/json`   |
| 502    | `LLM_UNAVAILABLE`        | the LLM could not be reached or failed     |
| 500    | `INTERNAL_ERROR`         | anything else                              |
