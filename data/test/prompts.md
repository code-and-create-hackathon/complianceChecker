# Test-Prompts (fiktiv)

Alle Daten sind erfunden. Die IBAN ist die offizielle Beispiel-IBAN, die E-Mail-Adressen nutzen reservierte Beispiel-Domains.

## Erwartet: sicher

| # | Prompt |
|---|---|
| S1 | Fasse die aktuelle Marktlage für europäische Staatsanleihen in fünf Stichpunkten zusammen. |
| S2 | Erkläre einem Privatkunden in einfachen Worten den Unterschied zwischen ETF und aktiv gemanagtem Fonds. |
| S3 | Formuliere eine freundliche Zahlungserinnerung an [KUNDE] für die Rechnung vom [DATUM]. |

## Erwartet: Risiko

| # | Prompt | Erwartete Probleme |
|---|---|---|
| R1 | Bitte formuliere eine Mahnung an max.muster@example.com wegen der offenen Zahlung auf DE89 3704 0044 0532 0130 00, Kundennummer: KD-482913. Streng vertraulich. | 4 (IBAN, E-Mail, Kundennummer, vertraulich) |
| R2 | Der Kunde hat mit der Karte 4111 1111 1111 1111 bezahlt. Schreib ihm eine Bestätigung. | 1 (Kartennummer) |
| R3 | Fasse diese vertrauliche Präsentation zur Übernahme für den Vorstand zusammen. | 1 (vertraulich) |
| R4 | Schick die Depotauszüge an anna.beispiel@example.de und an jonas.test@example.org. | 1 (E-Mail, 2 Fundstellen) |
| R5 | Prüfe bitte den Vertrag zu Vertragsnummer 2024-77812 auf Kündigungsfristen. | 1 (Vertragsnummer) |
