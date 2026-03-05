# Handoff: Place-Order Signature Fix

## Goal

Fix the failing test `OrderTest.placeOrder_withValidSignature_returns202`. The place-order API was returning 400 with:

```
signature validation failed: recovered address ... does not match expected signer ...
```

**Cause:** The EIP-712 message signed in sig-server did not match what the backend hashes for verification.

---

## What Was Done

### 1. EIP-712 domain in sig-server (`sig-server/signatures/server.js`)

- Use **chainId as string** so it matches the frontend/backend: `chainId: String(config.CHAIN_ID)` (i.e. `"84532"`). Using a number produced a different domain separator and wrong recovered address.

### 2. UAT verifying contract (`sig-server/config.js`)

- Set `VERIFYING_CONTRACT` to the frontend constant: `0x398e870065121Ee3b0565e2b925cBD3f25df2Ce1` (fixed for UAT, no env override).

### 3. Alignment with frontend EIP-712 payload

- **Domain:** `name: "Pred CTF Exchange"`, `version: "1"`, `chainId: "84532"`, `verifyingContract: "0x398e870065121Ee3b0565e2b925cBD3f25df2Ce1"`.
- **Order:** 12 fields, same order and types: salt, maker, signer, taker, price, quantity, expiration, nonce, questionId, feeRateBps, intent, signatureType.
- **Message:** price/quantity/expiration/nonce/salt as strings; feeRateBps/intent as numbers; signatureType 2.

---

## Where We Are Now

- Sig-server's EIP-712 domain and Order signing match the frontend payload (from the shared console logs).
- `placeOrder_withValidSignature_returns202` was reported passing after the chainId string fix.
- No open code changes for the place-order signature flow; config is set for UAT.

---

## What To Do Next

1. **Confirm:** Run full suite (e.g. `mvn clean test`) with sig-server up and UAT reachable; ensure all tests pass.
2. **Optional:** Add the two frontend EIP-712 payload examples to `docs/PLACE_ORDER_SIGNATURE.md` (or similar) as reference.
3. **If tests fail elsewhere:** Check that domain/verifying contract match that environment (e.g. different `VERIFYING_CONTRACT` for prod).

---

## Key Files

| Area | File |
|------|------|
| Signing | `sig-server/signatures/server.js` (domain, orderFields, /sign-order message) |
| Config | `sig-server/config.js` (DOMAIN_*, CHAIN_ID, VERIFYING_CONTRACT) |
| Test | `src/test/java/com/pred/apitests/test/OrderTest.java` (`placeOrder_withValidSignature_returns202`) |
| Request models | `SignOrderRequest.java`, `PlaceOrderRequest.java` |
