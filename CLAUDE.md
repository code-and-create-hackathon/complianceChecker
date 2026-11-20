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
- Work step by step: check the current state first, then make changes.
- MVP for the demo over perfection.
- Never commit secrets (`.env` + `.gitignore`).
- Follow all conventions for human coders in CONTRIBUTING.md.
- [Further conventions]
