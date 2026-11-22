# CLAUDE.md

## Project
Compliance gate for financial service providers: prompts first pass through our system and are checked against internal compliance rules (Markdown upload) before they are sent to an AI system. GDPR is out of scope.

- **Stage 1:** Online website
- **Stage 2:** Chrome extension

## Stack
- LLM: Amazon Nova
- Hosting: AWS (no local hosting)
- Frontend: plain HTML/CSS/JS (ES modules, no framework) in `frontend/`; Chrome extension with WXT in `extension/`
- Backend: Spring Boot (Java 21) in `backend/`, also serves the website
- Database: H2 file database (PostgreSQL driver included, switch via `HISTORY_DB_URL`)

## Rules
- **Everything in this repo is always in English:** user interface texts, code, identifiers, comments, documentation, test data, file and folder names, commit messages and PR descriptions.
- Work step by step: check the current state first, then make changes.
- MVP for the demo over perfection.
- Never commit secrets (`.env` + `.gitignore`).
- Follow all conventions for human coders in CONTRIBUTING.md.
