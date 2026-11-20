# CLAUDE.md

## Projekt
Compliance-Gate für Finanzdienstleister: Prompts laufen zuerst durch unser System und werden gegen interne Compliance-Regeln (Markdown-Upload) geprüft, bevor sie an ein KI-System gehen. DSGVO ist außerhalb des Scopes.

- **Stage 1:** Online-Website
- **Stage 2:** Chrome-Extension

## Stack
- LLM: Anthropic
- Hosting: AWS (kein lokales Hosting)
- [Frontend] · [Backend] · [Datenbank]

## Regeln
- Schrittweise arbeiten: erst den aktuellen Stand prüfen, dann ändern.
- MVP für die Demo vor Perfektion.
- Keine Secrets committen (`.env` + `.gitignore`).
- [Weitere Konventionen]
