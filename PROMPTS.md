# PROMPTS.md

Protokoll aller Prompts (wörtlich, chronologisch). Format pro Eintrag: `## [Datum Uhrzeit] – [Name]`, Prompt als Zitat, dann `Ergebnis:`.

---

## 2026-11-20 18:19 – Luca

> Erstelle im Root-Verzeichnis dieses Repos eine Datei CLAUDE.md mit genau folgendem Inhalt:
>
> ---
> # CLAUDE.md
>
> ## Projekt
> Compliance-Gate für Finanzdienstleister: Prompts laufen zuerst durch unser System und werden gegen interne Compliance-Regeln (Markdown-Upload) geprüft, bevor sie an ein KI-System gehen. DSGVO ist außerhalb des Scopes.
>
> - **Stage 1:** Online-Website
> - **Stage 2:** Chrome-Extension
>
> ## Stack
> - LLM: Anthropic
> - Hosting: AWS (kein lokales Hosting)
> - [Frontend] · [Backend] · [Datenbank]
>
> ## Regeln
> - **Jeder Prompt wird sofort und wörtlich in `PROMPTS.md` angehängt, bevor die Arbeit beginnt.** Format: `## [Datum Uhrzeit] – [Name]`, dann der Prompt als Zitat, dann `Ergebnis:` in 1–2 Sätzen.
> - Schrittweise arbeiten: erst den aktuellen Stand prüfen, dann ändern.
> - MVP für die Demo vor Perfektion.
> - Keine Secrets committen (`.env` + `.gitignore`).
> - [Weitere Konventionen]
> ---
>
> Lege außerdem PROMPTS.md an, falls sie fehlt, und trage diesen Auftrag als ersten Eintrag ein.
>
> Committe beide Dateien mit der Message "Add CLAUDE.md and PROMPTS.md" und pushe auf den aktuellen Branch. Zeige mir danach den Commit-Hash und den Status.

Ergebnis: CLAUDE.md (Projektregeln) und PROMPTS.md angelegt und lokal mit der Message "Add CLAUDE.md and PROMPTS.md" committet; der Push auf `origin/main` scheiterte mit 403 (Berechtigung fehlt für den Git-Benutzer `Luca0715`).
