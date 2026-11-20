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
