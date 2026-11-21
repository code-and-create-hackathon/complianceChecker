# CLAUDE.md

## Project
Compliance gate for financial service providers: prompts first pass through our system and are checked against internal compliance rules (Markdown upload) before they are sent to an AI system. GDPR is out of scope.

- **Stage 1:** Online website
- **Stage 2:** Chrome extension

## Stack
- LLM: Anthropic
- Hosting: AWS (no local hosting)
- [Frontend] · [Backend] · [Database]

## Rules
- **Everything in this repo is always in English:** user interface texts, code, identifiers, comments, documentation, test data, file and folder names, commit messages and PR descriptions.
- Work step by step: check the current state first, then make changes.
- MVP for the demo over perfection.
- Never commit secrets (`.env` + `.gitignore`).
- Follow all conventions for human coders in CONTRIBUTING.md.
- [Further conventions]
