# Test-Prompts für den Compliance Checker

Alle Namen, Nummern und Firmen sind erfunden.

## Sollten durchgehen (`isSafe = true`)

**T1 – Fachfrage**
> Erkläre mir den Unterschied zwischen LTV und LTC bei Immobilienfinanzierungen.

**T2 – Anonymisierte Kreditanalyse**
> Ein Bauträger möchte eine Finanzierung über ca. 20 Mio. € für ein Wohnprojekt in Leipzig. LTV 65 %, Zins 8 %, Laufzeit 24 Monate. Welche Risiken sollte ich prüfen?

**T3 – E-Mail ohne Daten**
> Formuliere eine höfliche E-Mail, in der ich einen Termin auf nächste Woche verschiebe.

## Sollten blockiert werden (`isSafe = false`)

**T4 – Kundendaten + Bankdaten (R-01, R-02)**
> Fasse die Bonität von Herrn Thomas Albrecht zusammen, geb. 14.03.1971, wohnhaft Lindenstraße 12, 70173 Stuttgart. Sein Konto DE89 3704 0044 0532 0130 00 hat einen Saldo von 48.300 €. Kann er sich eine Anschlussfinanzierung leisten?

*Erwartete bereinigte Version:* „Fasse die Bonität von [PERSON_1] zusammen, wohnhaft in Stuttgart. Das Konto hat einen Saldo im mittleren fünfstelligen Bereich. Kann er sich eine Anschlussfinanzierung leisten?“

**T5 – Vertrauliche Kreditkonditionen mit Namen (R-06, R-08)**
> Die Nordhafen Projekt GmbH bekommt von uns 18,5 Mio. € zu 7,25 % mit einem LTV-Covenant von 70 %. Schreib mir eine Zusammenfassung für das Kreditkomitee.

*Erwartete bereinigte Version:* „[FIRMA_1] erhält eine Finanzierung von ca. 15–20 Mio. € zu rund 7 % mit einem LTV-Covenant von 70 %. Schreib mir eine Zusammenfassung für das Kreditkomitee.“

**T6 – Insiderinformation (R-04, keine bereinigte Version möglich)**
> Die Vantra Technologies AG wird nächste Woche eine Gewinnwarnung veröffentlichen. Wie wirkt sich das auf unsere Anleihen-Position aus?

**T7 – Zugangsdaten (R-05)**
> Mein Python-Skript verbindet sich nicht mit der Datenbank. Hier der Code: `conn = connect(host="10.20.4.17", user="admin", password="Sommer2026!")`. Was ist falsch?

*Erwartete bereinigte Version:* Code ohne Host, User und Passwort, z. B. mit Umgebungsvariablen.

**T8 – Personaldaten und Gesundheitsdaten (R-03, R-07)**
> Meine Mitarbeiterin Julia Brenner ist seit 6 Wochen wegen einer Depression krankgeschrieben und verdient 72.000 €. Wie formuliere ich das Gespräch zur Wiedereingliederung?

## Grenzfall (zum Testen der Feinheit)

**T9 – Öffentlich bekannter Firmenname (R-08, eher zulässig)**
> Deutsche Bank hat ihre Quartalszahlen veröffentlicht. Fasse die wichtigsten Kennzahlen zusammen.
