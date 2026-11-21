# complianceChecker

## The problem
people post sensitve data in AI chats neglecting their compliance duties.

## Roadmap
**Prio 1**
 - compliance as external website first (minimal viable version)
 - as webbrowser extension

**Prio 2**
- add suggestions cencoring data
- company accounts
  
**Prio 3**
 - token optimisation & prompt engineering.


## Configurable LLM
It can run on a company server, possibly locally, behind a well-defined API.
Avoids potential gdpr problems if servers are self-hosted.

## Infrasturcture - final version

```mermaid
flowchart LR
    A[👤 User<br/>Extension] -->|prompt + compliance context| B[⚙️ Backend<br/>Compliance Checker]
    B -->|check| L[🤖 LLM<br/>configurable]
    B -->|reads| D[(📚 Compliance Guides<br/>Company Policies)]
    B -->|isSafe / problems / improvedVersion| A
    A -->|if isSafe = true| C[💬 Target LLM Chat]
```

## Infra-structure - first version


```mermaid
flowchart LR
    A[👤 User<br/>Website] -->|prompt + compliance guides| L[🤖 LLM<br/>configurable]
    L -->|isSafe / problems / improvedVersion| A
    A -->|if isSafe = true| C[💬 Target LLM Chat]
```

The API is defined [here](API.md).

## Chrome extension (Stage 2, in progress)

Lives in [`extension/`](extension/), built with [WXT](https://wxt.dev) (Manifest V3). It reuses the website's
contract code from `frontend/js/shared/` via the `@shared` alias, so that folder must stay free of `window`/DOM access.

```bash
cd extension
npm install
npm run dev     # opens Chrome with the extension loaded and hot reload
npm run build   # → extension/.output/chrome-mv3
```

To load a build manually: `chrome://extensions` → Developer mode → *Load unpacked* → `extension/.output/chrome-mv3`.

On `chatgpt.com` the extension holds back every send (Enter or the send button), checks the prompt against the
uploaded rules and only sends it if it is compliant. Blocked prompts show the problems and, if available, a
suggested version. If the check cannot run, nothing is sent.

**Backend:** the endpoint is set once for website and extension in `frontend/js/shared/config.js` (`API_URL`,
absolute URL); rebuild the extension after changing it. While it is empty the extension runs in demo mode
(local mock check, nothing leaves the browser).

**Try it:** click the toolbar icon → upload a rules file (e.g. `Testdata/compliance-guide-musterfinanz.md`) → on
chatgpt.com send `Pay DE89 3704 0044 0532 0130 00` (blocked, suggests `[IBAN_1]`) and `Hello` (sent normally).
