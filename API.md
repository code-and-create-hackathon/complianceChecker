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
  "improvedVersion": "Pay [IBAN]"
}
```

| Field             | Type     | Rules                                  |
|-------------------|----------|----------------------------------------|
| `isSafe`          | boolean  | `true` ⇔ `problems` is empty           |
| `problems`        | string[] | non-empty strings                      |
| `improvedVersion` | string   | `""` = no suggestion                   |

## General

- All fields required; no `null`; no extra fields.
- The web client aborts a request after 60 s (`REQUEST_TIMEOUT_MS` in `frontend/js/shared/config.js`); the backend should answer within that time.
