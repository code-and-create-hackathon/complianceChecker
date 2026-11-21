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
      "category": "Kontodaten"
    }
  ],
  "improvedVersion": "Pay [Kontodaten]"
}
```

| Field             | Type      | Rules                                                       |
|-------------------|-----------|-------------------------------------------------------------|
| `isSafe`          | boolean   | `true` exactly when `problems` is empty                    |
| `problems`        | object[]  | one entry per violated rule; `[]` = no violations          |
| `improvedVersion` | string    | censored input; unchanged input when there are no violations |

### `problems[]`

| Field      | Type   | Rules                                                           |
|------------|--------|-----------------------------------------------------------------|
| `ruleId`   | string | non-empty rule ID from the compliance rules        |
| `category` | string | non-empty top-level category from the matched rule |

The API deliberately does not return the matched raw text. `improvedVersion` is created by the model from the
original prompt and replaces only violating text with `[CATEGORY]`. The backend rejects a simulated model result
with HTTP `502` (`LLM_SIMULATED_RESULT`) and never returns it as a compliance decision.

## General

- All fields required; no `null`; no extra fields.
- The web client aborts a request after 60 s (`REQUEST_TIMEOUT_MS` in `frontend/js/shared/config.js`); the backend should answer within that time.
- The backend reads its fixed system instructions from
  [`compliance-hardprompt.md`](backend/src/main/resources/prompts/compliance-hardprompt.md). The API request keeps
  compliance rules and user input in separate fields; the backend adds a per-request random boundary around the
  untrusted user input before calling Nova.

## `GET /api/nova/status`

When the Nova console is enabled, this endpoint returns `connection` (`not_configured`, `reachable`, or
`unreachable`), `lastOutcome` (`unverified`, `success`, or `failed`), and `lastLatencyMs` (integer or `null`).
Reachability checks the AWS endpoint without sending a prompt. `lastOutcome` and `lastLatencyMs` describe the last
Nova request since the credentials were entered; the latency is `null` until a request completes.
