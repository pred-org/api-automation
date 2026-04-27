# Session Helper (Runbook)

Use this as a quick checklist when starting or handing off a session.

## 1) Pre-run checks

- Verify env inputs:
  - `CANONICAL_NAME`
  - `TOKEN_ID`
  - user 1 credentials
  - user 2 session (`.env.session2`) if two-user tests are included
- Confirm suite source:
  - `src/test/resources/suite.xml`
- Confirm dependencies are ready:
  - backend environment healthy
  - signature service reachable

## 2) Standard run commands

- Full suite:
  - `mvn clean test`
- Compile-only sanity:
  - `mvn test-compile`
- Single class:
  - `mvn -Dtest=OrderFlowTest test`

## 3) Fast triage flow

- If failure is `401`:
  - check refresh path used by that class
  - verify user 2 refresh if it is a `*User2` test
- If failure is "not visible in time":
  - check polling timeout and API visibility lag
  - inspect open-orders/positions payload shape
- If failure is market mismatch:
  - confirm filter checks both sub and parent ids where relevant
- If failure is open-order count:
  - inspect raw open-orders payload and matching keys (`market_id`, `marketId`, nested market fields)

## 4) Skip interpretation

- Expected skip categories:
  - env preconditions not met
  - no liquidity/no open positions
  - optional endpoint not available
  - explicitly disabled test
- Action:
  - only treat as issue if skip is unexpected for your run goal.

## 5) Artifacts to inspect after run

- `target/surefire-reports/*`
  - exact failing method and stack trace
- terminal output
  - latest API payload snippets
- modified tests
  - check whether retry/poll/assert logic is market-scoped and user-scoped

## 6) Handoff template

When sharing status, capture:

- build result (`pass/fail`, failure count, skip count)
- exact failing methods
- observed payload mismatch (if any)
- what was changed (test/assert/retry/poll)
- what remains to validate
