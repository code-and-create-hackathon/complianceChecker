# Daten

| Ordner | Inhalt | Im Repo? |
|---|---|---|
| `test/` | Fiktive Testdaten: Compliance-Regeln und Beispiel-Prompts | Ja |
| `real/` | Echte Compliance-Regeln und echte Prompts | **Nein**, per `.gitignore` ausgeschlossen |

Echte interne Dokumente und Kundendaten gehören nicht ins Repo (siehe [CONTRIBUTING.md](../CONTRIBUTING.md)). Legt sie nur lokal in `real/` ab.

## Testen

1. Seite mit Live Server öffnen: `http://127.0.0.1:5500/frontend/index.html`
2. `test/compliance-richtlinien.md` hochladen.
3. Prompts aus `test/prompts.md` einfügen und das Ergebnis mit der erwarteten Spalte vergleichen.

Die erwarteten Ergebnisse gelten für den Demo-Modus (`frontend/js/shared/mock_llm.js`). Mit dem echten Backend können sie abweichen.
