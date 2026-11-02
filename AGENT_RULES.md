# Agent Rules & Git Policy for BookMate

This document defines strict operational guidelines that all AI assistants and agents working on the BookMate project must follow across all chat sessions.

---

## 1. Commit and Push Protocol

- **Never auto-commit or auto-push without explicit confirmation**:
  - Whenever the user asks to commit or push changes to GitHub, if they have not provided the exact commit title or told you to do it completely, **always stop and ask for the commit title first**.
  - **Always suggest a clear, concise, and descriptive commit title** (and optional bullet points).
  - Wait for the user to confirm or update the title before running `git add`, `git commit`, or `git push`.

---

## 2. Secrets & Sensitive Data Prevention

- **Zero-Tolerance for Leaked Credentials**:
  - Never stage or commit any passwords, credentials, API keys, private tokens, IDs, or secrets to Git.
  - Before running `git add` or `git commit`, always audit the diff and untracked list (`git status`, `git diff`) to verify no credentials or private data are included.

- **Files that must NEVER be committed**:
  - `google-services.json` (Firebase client config containing project IDs / client IDs / app IDs).
  - `local.properties` (Android SDK paths and developer-specific environment variables).
  - Keystores & signing keys (`*.jks`, `*.keystore`, `*.pem`, `*.key`, `*.p12`, `*.pfx`).
  - Secret properties (`secrets.properties`, `signing.properties`, `.env`, `.env.*`).
  - Build outputs & caches (`.gradle/`, `build/`, `*.apk`, `*.aab`, `*.hprof`).

---

## 3. Maintaining `.gitignore`

- Ensure `.gitignore` is kept up to date and strictly prevents accidental tracking of build artifacts, developer-local properties, and credential files.
- If a new external integration or service is introduced (e.g., third-party SDKs, payment gateways, custom APIs), verify and add its credential files to `.gitignore` before writing any code.
