# Compliance Policy: Use of AI Chat Tools

**Company:** Musterfinanz AG (fictional test company)
**Applies to:** all employees, working students and external service providers
**As of:** November 2026 · Version 1.0
**Legal basis:** GDPR, BDSG (German Federal Data Protection Act), banking secrecy, MaRisk (AT 7.2), DORA, MAR (Market Abuse Regulation), EU AI Act

---

## 1. Principle

External AI chat tools (e.g. ChatGPT, Claude, Gemini, Copilot) may be used **as long as no protected information is entered**. Anything entered into an external AI tool is considered **transmitted to a third party outside the company**.

When in doubt: **anonymise the data or do not enter it.**

---

## 2. Severity levels

| Level | Meaning | Consequence for the prompt |
|---|---|---|
| **CRITICAL** | Violation of law or supervisory regulation | Prompt is blocked (`isSafe = false`) |
| **HIGH** | Violation of internal policy with significant risk | Prompt is blocked, a sanitised version is suggested |
| **MEDIUM** | Increased risk, but permitted after anonymisation | Prompt is blocked, a sanitised version is suggested |
| **LOW** | Notice, no violation | Prompt may be sent (`isSafe = true`), a notice is shown |

---

## 3. Prohibited content

### R-01 Personal data of customers (CRITICAL)
No data that can be used to identify a natural person:
- full names of customers, guarantors, tenants or contact persons
- private addresses, private phone numbers, private email addresses
- dates of birth, tax IDs, social security numbers
- ID card or passport numbers

**Permitted:** placeholders such as "Customer A", "Guarantor 1", "[NAME]".

### R-02 Banking secrecy and account data (CRITICAL)
- IBANs, account numbers, credit card numbers
- account balances, transactions, payment histories of individual customers
- loan numbers

**Permitted:** none. This data must not be entered, not even partially.

### R-03 Special categories of personal data (CRITICAL)
According to Art. 9 GDPR:
- health data (e.g. illnesses, sick leave of employees)
- religion, political opinions, trade union membership
- ethnic origin, sexual orientation

### R-04 Insider information (CRITICAL)
According to Art. 7 MAR:
- unpublished information about listed companies, e.g. planned takeovers, profit warnings, capital measures
- information about upcoming customer transactions that could affect the share price

### R-05 Credentials and security information (CRITICAL)
- passwords, API keys, tokens, certificates
- internal server addresses, IP addresses, network diagrams
- details of security vulnerabilities or IT incidents (DORA)

### R-06 Confidential business information (HIGH)
- terms of specific loan agreements (interest rate, margin, covenants) **in combination with the borrower's name**
- unpublished financial figures of Musterfinanz AG
- content from management board or supervisory board meetings
- data from ongoing M&A processes
- content from data rooms or due diligence documents

**Permitted:** anonymised terms without reference to an identifiable borrower, e.g. "a property developer, LTV 65%, interest rate 7.5%".

### R-07 Employee HR data (HIGH)
- salaries, bonuses, performance reviews of individual employees
- warnings, dismissals, HR meetings
- job applications with names

### R-08 Names of corporate clients and business partners (MEDIUM)
Company names of borrowers, investors or project companies may only be mentioned if the information is already public (e.g. press release).

**Permitted after anonymisation:** "Project company X", "Investor B", "a pension fund".

### R-09 Internal documents with a confidentiality marking (MEDIUM)
Texts marked "confidential", "internal" or "strictly confidential" must not be pasted in full. General wording help without sensitive content is permitted.

---

## 4. Permitted use (examples)

The following is **explicitly permitted** (`isSafe = true`):
- general domain questions, e.g. "What is a DSCR?" or "Explain MaRisk AT 4.3"
- help with wording emails without customer data
- summarising publicly available texts (laws, press releases, articles)
- Excel formulas, code without credentials, presentation structure
- calculations with made-up or anonymised figures

---

## 5. Rules for the sanitised version (`improvedVersion`)

If a prompt is blocked, the Compliance Checker should suggest a sanitised version:

1. **Personal names** → `[PERSON_1]`, `[PERSON_2]` …
2. **Customer company names** → `[COMPANY_1]`, `[COMPANY_2]` …
3. **Addresses** → `[ADDRESS]` (the city may remain if needed for the content)
4. **IBAN, account, loan and ID numbers** → remove completely
5. **Exact amounts** → round or give as a range (e.g. "approx. €10–15m")
6. **Credentials** → remove completely, never carry over masked
7. **Insider information** → no sanitised version possible; prompt remains blocked
8. The user's **actual question** must be preserved after sanitisation.

---

## 6. Contacts

If in doubt: Compliance department, compliance@musterfinanz.example
Data protection officer: datenschutz@musterfinanz.example
