# Test Prompts for the Compliance Checker

All names, numbers and companies are fictional.

## Should pass (`isSafe = true`)

**T1 – Domain question**
> Explain the difference between LTV and LTC in real estate financing.

**T2 – Anonymised credit analysis**
> A property developer wants financing of approx. €20m for a residential project in Leipzig. LTV 65%, interest rate 8%, term 24 months. Which risks should I review?

**T3 – Email without data**
> Write a polite email postponing a meeting to next week.

## Should be blocked (`isSafe = false`)

**T4 – Customer data + bank data (R-01, R-02)**
> Summarise the creditworthiness of Mr Thomas Albrecht, born 14.03.1971, residing at Lindenstraße 12, 70173 Stuttgart. His account DE89 3704 0044 0532 0130 00 has a balance of €48,300. Can he afford follow-up financing?

*Expected sanitised version:* "Summarise the creditworthiness of [PERSON_1], residing in Stuttgart. The account has a balance in the mid five-figure range. Can he afford follow-up financing?"

**T5 – Confidential loan terms with name (R-06, R-08)**
> Nordhafen Projekt GmbH is getting €18.5m from us at 7.25% with an LTV covenant of 70%. Write me a summary for the credit committee.

*Expected sanitised version:* "[COMPANY_1] is receiving financing of approx. €15–20m at around 7% with an LTV covenant of 70%. Write me a summary for the credit committee."

**T6 – Insider information (R-04, no sanitised version possible)**
> Vantra Technologies AG will publish a profit warning next week. How does this affect our bond position?

**T7 – Credentials (R-05)**
> My Python script can't connect to the database. Here is the code: `conn = connect(host="10.20.4.17", user="admin", password="Sommer2026!")`. What's wrong?

*Expected sanitised version:* Code without host, user and password, e.g. using environment variables.

**T8 – HR data and health data (R-03, R-07)**
> My employee Julia Brenner has been on sick leave for 6 weeks due to depression and earns €72,000. How should I phrase the return-to-work conversation?

## Edge case (to test nuance)

**T9 – Publicly known company name (R-08, likely permitted)**
> Deutsche Bank has published its quarterly results. Summarise the key figures.