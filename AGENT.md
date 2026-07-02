# AGENT.md

Single source of contributor and AI-agent guidance for this repository.

## Scope

BurpSense has two parts:
- burp-bridge: Java 21 + Maven Burp Suite extension.
- vscode-extension: TypeScript VS Code extension.

## Ground Rules

- Keep changes minimal and targeted.
- Fix root causes, not symptoms.
- Do not refactor unrelated code in the same change.
- Prefer straightforward code over clever abstractions.
- Avoid AI-style boilerplate: no generic wrappers, no speculative patterns, no unused helpers.
- Keep functions and classes focused; split only when readability clearly improves.

## Decision Heuristics

- If a change can be done in one place, do not introduce a new abstraction.
- If logic is duplicated twice, keep it local unless duplication is already causing bugs.
- If a new dependency is optional, do not add it.
- Favor explicit names and simple control flow over dense patterns.

## Quality Bar

- Match existing style in touched files.
- Add tests for behavior changes where tests already exist nearby.
- Update docs only when behavior or commands change.
- Never commit secrets, tokens, or local machine paths.

## Security and Privacy

- Validate all external input at boundaries (API, settings, file paths).
- Keep bridge communication local by default; avoid widening network exposure.
- Do not log secrets, API keys, or raw sensitive payloads.
- Prefer safe defaults and fail-closed behavior for auth and filtering.

## Build and Test

From repository root:

- Bridge build: cd burp-bridge && mvn clean package
- Bridge tests: cd burp-bridge && mvn clean test && mvn verify -DskipUnitTests
- Extension install: cd vscode-extension && npm ci
- Extension compile: cd vscode-extension && npm run compile
- Extension lint: cd vscode-extension && npm run lint

## Fast Test Matrix

- Bridge API, handlers, middleware, auth, or settings changed: run bridge unit and integration tests.
- VS Code commands, providers, mapping, diagnostics, or connection code changed: run extension compile and lint; run tests when present.
- Shared contract or issue payload shape changed: run both bridge and extension verification commands.

## Commit Convention (Required)

Use Conventional Commits. This repository release flow depends on it.

Format:

- <type>(<scope>): <short summary>

Allowed types:

- feat, fix, docs, refactor, test, chore, ci, perf, revert

Examples:

- feat(connection): add optional auto-connect setting
- fix(mapping): harden path normalization against traversal
- chore(release): bump version to 1.1.0

Rules:

- Use imperative mood.
- Keep subject concise.
- Include scope when it adds clarity.
- Mark breaking changes with BREAKING CHANGE in the footer.

## PR Expectations

- One logical change per PR.
- Include a short verification note listing commands run.
- If tests were skipped, state why.
- If behavior or config contract is breaking, include migration notes in the PR body.

## Logging Policy

- Never log API keys, tokens, secrets, or full raw request and response payloads.
- Log only the minimum context needed for debugging, with stable structured keys.

## Definition of Done

- Code compiles in changed module(s).
- Relevant tests pass, or skip reason is documented.
- No obvious regression in connection, mapping, or advisory flows.
- Commit message follows Conventional Commits.
