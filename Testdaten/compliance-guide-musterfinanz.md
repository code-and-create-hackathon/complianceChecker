# Compliance-Richtlinie: Nutzung von KI-Chat-Tools

**Unternehmen:** Musterfinanz AG (fiktives Testunternehmen)
**Gültig für:** alle Mitarbeitenden, Werkstudierenden und externen Dienstleister
**Stand:** November 2026 · Version 1.0
**Rechtsgrundlagen:** DSGVO, BDSG, Bankgeheimnis, MaRisk (AT 7.2), DORA, MAR (Marktmissbrauchsverordnung), EU AI Act

---

## 1. Grundsatz

Externe KI-Chat-Tools (z. B. ChatGPT, Claude, Gemini, Copilot) dürfen genutzt werden, **solange keine geschützten Informationen eingegeben werden**. Alles, was in ein externes KI-Tool eingegeben wird, gilt als **an einen Dritten außerhalb des Unternehmens übermittelt**.

Im Zweifel gilt: **Daten anonymisieren oder nicht eingeben.**

---

## 2. Schweregrade

| Stufe | Bedeutung | Konsequenz für den Prompt |
|---|---|---|
| **KRITISCH** | Verstoß gegen Gesetz oder Aufsichtsrecht | Prompt wird blockiert (`isSafe = false`) |
| **HOCH** | Verstoß gegen interne Richtlinie mit erheblichem Risiko | Prompt wird blockiert, bereinigte Version wird vorgeschlagen |
| **MITTEL** | Erhöhtes Risiko, aber mit Anonymisierung zulässig | Prompt wird blockiert, bereinigte Version wird vorgeschlagen |
| **NIEDRIG** | Hinweis, kein Verstoß | Prompt darf gesendet werden (`isSafe = true`), Hinweis wird angezeigt |

---

## 3. Verbotene Inhalte

### R-01 Personenbezogene Daten von Kunden (KRITISCH)
Keine Daten, mit denen sich eine natürliche Person identifizieren lässt:
- vollständige Namen von Kunden, Bürgen, Mietern oder Ansprechpartnern
- Privatadressen, private Telefonnummern, private E-Mail-Adressen
- Geburtsdaten, Steuer-IDs, Sozialversicherungsnummern
- Ausweis- oder Passnummern

**Zulässig:** Platzhalter wie „Kunde A“, „Bürge 1“, „[NAME]“.

### R-02 Bankgeheimnis und Kontodaten (KRITISCH)
- IBANs, Kontonummern, Kreditkartennummern
- Kontostände, Umsätze, Zahlungshistorien einzelner Kunden
- Kreditnummern bzw. Darlehensnummern

**Zulässig:** keine. Diese Daten dürfen auch nicht teilweise eingegeben werden.

### R-03 Besondere Kategorien personenbezogener Daten (KRITISCH)
Nach Art. 9 DSGVO:
- Gesundheitsdaten (z. B. Krankheiten, Krankschreibungen von Mitarbeitenden)
- Religion, politische Meinung, Gewerkschaftszugehörigkeit
- ethnische Herkunft, sexuelle Orientierung

### R-04 Insiderinformationen (KRITISCH)
Nach Art. 7 MAR:
- noch nicht veröffentlichte Informationen über börsennotierte Unternehmen, z. B. geplante Übernahmen, Gewinnwarnungen, Kapitalmaßnahmen
- Informationen über anstehende Transaktionen von Kunden, die den Kurs beeinflussen könnten

### R-05 Zugangsdaten und Sicherheitsinformationen (KRITISCH)
- Passwörter, API-Keys, Tokens, Zertifikate
- interne Server-Adressen, IP-Adressen, Netzwerkpläne
- Details zu Sicherheitslücken oder IT-Vorfällen (DORA)

### R-06 Vertrauliche Geschäftsinformationen (HOCH)
- Konditionen konkreter Kreditverträge (Zinssatz, Marge, Covenants) **in Verbindung mit dem Namen des Kreditnehmers**
- nicht veröffentlichte Finanzzahlen der Musterfinanz AG
- Inhalte aus Vorstands- oder Aufsichtsratssitzungen
- Daten aus laufenden M&A-Prozessen
- Inhalte von Datenräumen oder Due-Diligence-Unterlagen

**Zulässig:** anonymisierte Konditionen ohne Bezug zu einem identifizierbaren Kreditnehmer, z. B. „ein Bauträger, LTV 65 %, Zins 7,5 %“.

### R-07 Personaldaten von Mitarbeitenden (HOCH)
- Gehälter, Boni, Leistungsbeurteilungen einzelner Mitarbeitender
- Abmahnungen, Kündigungen, Personalgespräche
- Bewerbungsunterlagen mit Namen

### R-08 Namen von Firmenkunden und Geschäftspartnern (MITTEL)
Firmennamen von Kreditnehmern, Investoren oder Projektgesellschaften dürfen nur genannt werden, wenn die Information bereits öffentlich ist (z. B. Pressemitteilung).

**Zulässig nach Anonymisierung:** „Projektgesellschaft X“, „Investor B“, „ein Versorgungswerk“.

### R-09 Interne Dokumente mit Vertraulichkeitsvermerk (MITTEL)
Texte, die als „vertraulich“, „intern“ oder „streng vertraulich“ gekennzeichnet sind, dürfen nicht vollständig eingefügt werden. Allgemeine Formulierungshilfen ohne sensible Inhalte sind erlaubt.

---

## 4. Erlaubte Nutzung (Beispiele)

Folgendes ist **ausdrücklich erlaubt** (`isSafe = true`):
- allgemeine Fachfragen, z. B. „Was ist ein DSCR?“ oder „Erkläre MaRisk AT 4.3“
- Formulierungshilfe für E-Mails ohne Kundendaten
- Zusammenfassen öffentlich verfügbarer Texte (Gesetze, Pressemitteilungen, Fachartikel)
- Excel-Formeln, Code ohne Zugangsdaten, Präsentationsstruktur
- Berechnungen mit erfundenen oder anonymisierten Zahlen

---

## 5. Regeln für die bereinigte Version (`improvedVersion`)

Wenn ein Prompt blockiert wird, soll der Compliance Checker eine bereinigte Version vorschlagen:

1. **Personennamen** → `[PERSON_1]`, `[PERSON_2]` …
2. **Firmennamen von Kunden** → `[FIRMA_1]`, `[FIRMA_2]` …
3. **Adressen** → `[ADRESSE]` (Stadt darf bleiben, wenn für den Inhalt nötig)
4. **IBAN, Konto-, Kredit- und Ausweisnummern** → vollständig entfernen
5. **Exakte Beträge** → runden oder als Spanne angeben (z. B. „ca. 10–15 Mio. €“)
6. **Zugangsdaten** → vollständig entfernen, niemals maskiert übernehmen
7. **Insiderinformationen** → keine bereinigte Version möglich; Prompt bleibt blockiert
8. Die **inhaltliche Frage** des Nutzers muss nach der Bereinigung erhalten bleiben.

---

## 6. Ansprechpartner

Bei Unsicherheiten: Compliance-Abteilung, compliance@musterfinanz.example
Datenschutzbeauftragter: datenschutz@musterfinanz.example
