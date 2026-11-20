# API definition - first draft

## `POST /api/check`

Client to server payload (`Content-Type: application/json`):

```json
{
  "prompt": "",
  "compliance": ""
}
```

- `prompt`: required, not blank, max. 20,000 characters
- `compliance`: compliance rules (Markdown), required, not blank, max. 100,000 characters


Server to client payload (`200 OK`):

```json
{
  "isSafe": false,
  "problems": [
    "",
    ""
  ],
  "categories": ["CUSTOMER_DATA"],
  "improvedVersion": "",
  "simulated": true
}
```

- `categories` (addition): coarse problem categories, one of `CUSTOMER_DATA`, `ACCOUNT_DATA`, `INSIDER_INFO`, `CREDENTIALS`, `OTHER`
- `simulated` (addition): `true` if the result comes from the mock mode (`LLM_PROVIDER=mock`, the default). The mock only
  detects a few patterns (email, phone, credit card, IBAN, confidentiality keywords) and ignores the uploaded
  compliance rules. It is **not** a real compliance check.


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

Only these fields are stored. The **original prompt and the compliance rules are never stored.**
`improvedVersion` is `null` when it could not be stored safely: when it equals the original prompt (e.g. for safe
prompts), still contains detectable sensitive data, or is too long.


## Errors

All errors share one shape. Messages are fixed server-side texts and never contain any part of the submitted content.

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
