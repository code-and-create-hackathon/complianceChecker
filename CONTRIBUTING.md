# Contributing to Four Eyes

## Branches

- `main`: stable, demo-ready. No direct pushes.
- `dev`: integration branch; feature branches start here and merge back via PR.

## Workflow

1. Check the current state first (`git fetch`, read the relevant code and docs).
2. Small PRs, one topic each; say *what* changed and *why*.
3. MVP over perfection.
4. Everything in English: UI, code, comments, docs, test data, commits.

## Commit messages

Conventional and imperative: `feat: add payload validation`, `fix: README API link`.

## Secrets and data

- Never commit secrets (API keys, AWS credentials, admin codes). Use a git-ignored `.env`.
- Only fictional data in the repo, tests and example prompts.

## Docs

- Payload or error changes: update [API.md](API.md) in the same PR.
- Keep [README.md](README.md) short; details go into [docs/](docs).

## Scope

Stage 1 website, Stage 2 browser extension. LLM: Amazon Nova via Bedrock. Hosting: AWS. GDPR is out of scope;
don't expand scope without agreement.
