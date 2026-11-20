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
| `findings`        | object[] | **optional**, see below; `[]` if `isSafe` |

### `findings` (optional)

One entry per match, so the UI can mark it in the prompt and list it. Without `findings` the UI still works, just without marks.

```json
"findings": [
  {
    "id": 1, "ruleId": "R-02", "category": "IBAN", "severity": "crit",
    "message": "Contains an IBAN.", "match": "DE89 3704 0044 0532 0130 00",
    "start": 4, "end": 31, "replacement": "[IBAN_1]"
  }
]
```

| Field         | Type    | Rules                                                                 |
|---------------|---------|-----------------------------------------------------------------------|
| `id`          | integer | ≥ 1, shown as the finding number                                      |
| `ruleId`      | string  | rule from the compliance file, e.g. `R-02`                            |
| `category`    | string  | short label, e.g. `IBAN`                                              |
| `severity`    | string  | `crit` (violation) or `warn` (notice)                                 |
| `message`     | string  | explanation for the user                                              |
| `match`       | string  | the matched text                                                      |
| `start`/`end` | integer | position in `prompt`, `0 ≤ start < end`, as JS/Java string indices (UTF-16 code units) |
| `replacement` | string  | placeholder used in `improvedVersion`, e.g. `[IBAN_1]`                |

## General

- All fields required except `findings`; no `null`; no extra fields.
- The web client aborts a request after 60 s (`REQUEST_TIMEOUT_MS` in `frontend/js/shared/config.js`); the backend should answer within that time.
