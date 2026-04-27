# Objectives and Alignment — Backend API Automation

This document defines the shared objective, system model, test flows, and success criteria so we stay aligned. It is the primary design doc for **two users** and **state-transition validation** (earlier single-user-only notes were folded into this doc and into [GAPS-AND-ROADMAP.md](GAPS-AND-ROADMAP.md)).

---

## 1. Objective

Build backend API automation for a **prediction-market style trading system**.

The goal is to **validate**:

- User authentication
- Order placement
- **Order matching between two users**
- Position creation
- Balance transitions
- Market resolution
- Settlement and redeem correctness
- **No negative or duplicate balance updates**

This is **state-transition validation**, not just endpoint testing.

---

## 2. System Model

The backend behaves as a **state machine**:

```
Wallet Balance
    |
    v
Reserved Balance (Open Order)
    |
    v
Matched Order
    |
    v
Position Created
    |
    v
Market Resolved
    |
    v
Settlement
    |
    v
Redeemed Balance
```

Automation must validate **each state transition**.

---

## 3. Test Users

We require at least:

| Role       | User   | Description        |
|-----------|--------|--------------------|
| Buyer     | User A | Places buy orders  |
| Seller    | User B | Places sell orders |

Each user:

- Has EOA address
- Logs in via signature
- Gets `access_token`
- Gets `proxy_wallet_address`
- Enables trading
- Deposits funds

**All tests must use real authentication flow unless explicitly mocked.**

---

## 4. Primary Test Flow (Happy Path)

### Step 1 — Login

- Create API key (internal)
- Login with signature
- **Store:** `access_token`, `user_id`, `proxy_wallet_address`
- **Validate:** 200 status, token not null, proxy wallet present

### Step 2 — Deposit

- Deposit amount to `user_id`
- **Validate:** 2xx response, balance increases
- **Capture:** `initial_total_balance`

### Step 3 — Place Order (Single-User Validation)

- Place one order for User A
- **Validate:** 202 accepted, reserved balance increases, no position yet (if unmatched)

### Step 4 — Matching Scenario (Two Users)

- User A: BUY  
- User B: SELL  
- Same market, matching price  

**Validate after match:**

- Position created for both users
- Reserved reduced
- Position size correct
- No negative balance

---

## 5. Balance Validation Rules

For each user, validate:

- `total_balance`
- `available_balance`
- `reserved_balance`
- `position_balance`

**Rules:**

| State            | Rule                                      |
|------------------|-------------------------------------------|
| Unmatched order  | `available = total - reserved`; `position_size = 0` |
| After match      | `position_size > 0`; reserved reduced      |

Do **not** hardcode balances in assertions; derive expected values from prior steps and business rules.

---

## 6. Market Resolution and Settlement

After market resolves:

- **Winning side:** `final_balance = initial_balance + pnl`
- **Losing side:** `final_balance = initial_balance - cost`

**Validate:**

- Position state = settled
- Redeem only once
- No duplicate settlement
- No negative final balance

---

## 7. Critical Validations

The automation must **detect**:

| Case                     | What to validate                                  |
|--------------------------|---------------------------------------------------|
| Duplicate order (same salt) | Reject or idempotent; no double reserve           |
| Invalid signature        | 4xx; no state change                              |
| Expired timestamp        | Reject; no order created                          |
| Insufficient balance     | Reject; no reserved/position change               |
| Double redeem attempt    | Reject or idempotent; balance unchanged           |
| Partial fill correctness | Position and reserved match fill size             |
| Cancel order             | Reserved balance released                         |

---

## 8. Partial Fill Scenario

**Example:** User A buys 100; User B sells 40.

**Validate:**

- Position size = 40
- Remaining open order = 60
- Reserved adjusted correctly

---

## 9. What NOT To Do

- Do **not** hardcode balances in assertions
- Do **not** assume full match
- Do **not** skip reserved balance checks
- Do **not** test only happy path
- Do **not** rely on manual market resolution (automation must poll or trigger resolve when possible)

---

## 10. Test Suites Structure

| Suite                  | Focus                                      |
|------------------------|--------------------------------------------|
| **Suite 1 — Order Validation**   | Valid order; invalid payload; duplicate salt; expired order |
| **Suite 2 — Matching Engine**    | Full match; partial match; multiple matches; cancel order  |
| **Suite 3 — Balance Engine**     | Reserved logic; position creation; settlement correctness; no negative balance |
| **Suite 4 — Settlement**        | Winning long; winning short; losing case; double redeem prevention |

Implement via TestNG suite XML(s); see [FRAMEWORK.md](FRAMEWORK.md) and `src/test/resources/suite.xml`.

---

## 11. Config Requirements

All sensitive and environment-specific values must come from **environment** (or secure properties, not committed):

- `API_BASE_URI_PUBLIC`
- `API_BASE_URI_INTERNAL`
- `API_KEY`
- `MARKET_ID`
- `TOKEN_ID`
- `DEPOSIT_AMOUNT`
- Per-user: EOA, private key or sig-server (for login/order signatures)

**No secrets in code.**

See [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for endpoints and [NOTION_TASKS.md](NOTION_TASKS.md) for config tasks.

---

## 12. Success Criteria

Automation is considered correct if:

- Orders match correctly between two users
- Position reflects correct side and size
- Balance math is accurate (total, available, reserved, position)
- No negative balances
- No duplicate settlement
- All flows are fully automated; no manual intervention required

---

## 13. Design Principle

This automation validates **state integrity**, not just API status codes.

The goal is to protect:

- **Matching engine** correctness  
- **Balance engine** correctness  
- **Settlement engine** correctness  

---

## 14. Alignment with Existing Docs

| Doc / artifact            | Relationship to this document |
|---------------------------|-------------------------------|
| [GAPS-AND-ROADMAP.md](GAPS-AND-ROADMAP.md) | Coverage gaps and roadmap; complements this doc. |
| [FRAMEWORK.md](FRAMEWORK.md)         | Stack (TestNG, RestAssured, Service Object, POJOs, Filters, GitHub Actions), execution flow, and design annex (section 15). |
| [NOTION_TASKS.md](NOTION_TASKS.md)   | Task list from zero to redeem/balance; add tasks for two-user flows and suite structure above when implementing. |
| [API_DOCUMENTATION.md](API_DOCUMENTATION.md) | Canonical HTTP API reference (from Java services). |

When implementing: use **OBJECTIVES_AND_ALIGNMENT** for what to validate; use **API_DOCUMENTATION** and **FRAMEWORK** for how to implement; use **NOTION_TASKS** to track work.
