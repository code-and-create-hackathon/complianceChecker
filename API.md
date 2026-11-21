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

## General

- All fields required; no `null`; no extra fields.
- The web client aborts a request after 60 s (`REQUEST_TIMEOUT_MS` in `frontend/js/shared/config.js`); the backend should answer within that time.
