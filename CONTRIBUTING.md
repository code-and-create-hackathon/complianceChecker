# Contributing to complianceChecker

Thanks for helping out! This project is still at an early stage (MVP for the demo), so this guide is short and will grow with the codebase.

## Developer Quickstart

### Branch Structure
*   **`main`**: Stable, demo-ready branch. Please do not push directly here.
*   **`dev`**: Integration branch where features come together. Once tested, `dev` is merged into `main`.


### Workflow
1. Check the current state first (`git fetch`, read the relevant code/docs), then change things step by step.
2. Keep Pull Requests small and focused on one topic.
3. Describe *what* changed and *why* in the PR description.
4. MVP over perfection: a working demo comes first, polishing later.

### Commit Messages
imperative and descriptive, e.g. `feat: Add API payload validation`, `fix: README API link`.

### Secrets & Sensitive Data
This is a compliance tool, so let's practise what we preach:
*   **Never commit secrets** (API keys, AWS credentials, tokens). Use a local `.env` file, which must be listed in `.gitignore`.
*   Don't put real customer data or real internal compliance documents into the repo, tests or example prompts. Use made-up sample data.

### API Changes
The client/server contract lives in [API.md](API.md). If you change the payload (`prompt`, `compliance`, `isSafe`, `problems`, `improvedVersion`), update `API.md` in the same Pull Request.

### Architecture & Scope
See the [README](README.md) for the roadmap and architecture diagrams.
*   **Stage 1:** website · **Stage 2:** browser extension
*   LLM: Anthropic, configurable behind a well-defined API · Hosting: AWS
*   GDPR is out of scope for now. Please don't expand the scope without agreement.

