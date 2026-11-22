# Backend

Spring Boot (Java 21). Serves the website from `frontend/` and the API under `/api` ([API.md](../API.md)).

## Configuration

Environment variables; the defaults work without setup.

| Variable               | Default                       | Meaning                                                    |
|------------------------|-------------------------------|------------------------------------------------------------|
| `ADMIN_CODE`           | *(empty)*                     | Admin login code. **Required for admin login. Never commit.** |
| `ADMIN_USERNAME`       | `admin`                       | The one administrator, created at startup                  |
| `LLM_PROVIDER`         | `bedrock`                     | `bedrock` or `mock` (offline demo, explicit only)          |
| `AWS_PROFILE`          | *(AWS default chain)*         | AWS profile                                                |
| `AWS_REGION`           | `eu-north-1`                  | Bedrock region                                             |
| `LLM_MODEL_ID`         | `eu.amazon.nova-2-lite-v1:0`  | Bedrock model or inference profile                         |
| `HISTORY_DB_URL`       | `jdbc:h2:file:./data/history` | History database (PostgreSQL driver included)              |
| `HISTORY_DB_USER`      | `sa`                          | Database user                                              |
| `HISTORY_DB_PASSWORD`  | *(empty)*                     | Database password                                          |
| `PORT`                 | `8080`                        | HTTP port                                                  |
| `CORS_ALLOWED_ORIGINS` | `chrome-extension://*`        | Comma-separated origins allowed to call `/api/**`          |

## Amazon Bedrock

- Model: Nova 2 Lite via the Converse API. The EU inference profile keeps requests in EU regions
  (`eu-north-1` has no in-region access).
- Enable model access in the Bedrock console; the IAM principal needs `bedrock:InvokeModel` on the profile and the
  model in the EU regions it routes to.
- Credentials come from the AWS default chain. **Never put keys into code or config files.**
- **Every check is a paid call.**

How a check works:

1. `SensitiveDataScanner` replaces pattern matches (IBAN, email, …) with placeholders like `[IBAN_1]`.
2. Bedrock checks the sanitised prompt against the published policy and returns `ruleId` and `severity`.
3. Local and model findings are merged. Unknown severities become `critical`; a `location` not found verbatim becomes
   `""`; "unsafe" without a blocking problem gets an `UNSPECIFIED`/`critical` problem. A missing tool call is retried once.

Any AWS error returns `502 LLM_UNAVAILABLE`: the prompt is not approved and nothing is stored. The message names the
cause (credentials, access denied, model/region, busy, timeout after 50 s, network) and never echoes AWS output.

## Mock provider

Only with `LLM_PROVIDER=mock`, never as a fallback; a warning is logged at startup. It ignores the policy and reports
pattern matches with the rule ids of `Testdata/compliance-guide-musterfinanz.md`:

| Pattern                                                          | Rule   | Severity   | Placeholder                    |
|------------------------------------------------------------------|--------|------------|--------------------------------|
| Sentence with insider keyword (*profit warning*, *takeover bid*) | `R-04` | `critical` | `[REMOVED_n]`                  |
| Sentence with health data (*sick leave*, *diagnosis*)            | `R-03` | `critical` | `[REMOVED_n]`                  |
| Sentence with confidentiality keyword (*confidential*)           | `R-09` | `medium`   | `[REMOVED_n]`                  |
| Password/token/API key after `=`/`:`; internal IPv4              | `R-05` | `critical` | `[SECRET_n]`, `[IP_ADDRESS_n]` |
| IBAN; Luhn-valid card number                                     | `R-02` | `critical` | `[IBAN_n]`, `[CARD_NUMBER_n]`  |
| Email, phone, name after *Mr/Mrs/Herr/Frau*, birth date, street  | `R-01` | `critical` | `[EMAIL_n]`, `[NAME_n]`, …     |
| Company with legal form (*GmbH*, *AG*, *Ltd*)                    | `R-08` | `medium`   | `[COMPANY_n]`                  |

All prompts in `Testdata/test-prompts.md` pass as expected (`TestdataPromptsTest`).

## History

`GET /api/history` returns the latest 100 checks, newest first. Admin sees all users, workers their own.
Backend-only, not part of API.md.

```json
[{ "id": 2, "createdAt": "2026-11-21T10:00:00Z", "username": "erika", "isSafe": false,
   "findings": [{ "ruleId": "R-01", "severity": "critical" }] }]
```

## Code structure

```
backend/src/main/java/com/compliancechecker/
├── account/     users, demo login, AuthInterceptor (login + admin checks), admin bootstrap
├── policy/      versioned company policy
├── controller/  REST endpoints, error handling
├── service/     ComplianceService, SensitiveDataScanner
├── llm/         LlmClient: BedrockLlmClient, MockLlmClient
├── history/     check history (Spring Data JPA)
├── model/       request/response records
└── config/      Bedrock client, CORS
```
