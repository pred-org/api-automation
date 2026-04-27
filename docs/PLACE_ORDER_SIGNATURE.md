# Place-Order EIP-712 Signature (Making the Test Pass)

The test `OrderTest.placeOrder_withValidSignature_returns202` fails with:

```
signature validation failed: recovered address 0x... does not match expected signer 0x...
```

This means the **message the backend hashes** when verifying the signature is not identical to the **message we sign** in sig-server. EIP-712 is sensitive to:

- Domain: field **order** and values (`name`, `version`, `chainId`, `verifyingContract`)
- Order struct: **primary type name** (e.g. `Order` vs `LimitOrder`) and **field order**
- Field types and value encoding (uint256, bytes32, address, etc.)

## What We Need From the Backend

To align sig-server with the backend, we need the **exact** EIP-712 definition the backend uses:

1. **Domain**
   - Field order (e.g. `name`, `version`, `chainId`, `verifyingContract` or `chainId`, `name`, ...)
   - Values: `name`, `version`, `chainId`, `verifyingContract` (must match config)

2. **Order (or other) struct**
   - Primary type name (e.g. `Order`, `LimitOrder`, `OrderData`)
   - Field order and types, e.g.:
     - `salt` (uint256), `maker` (address), `signer` (address), `taker` (address),
     - `price` (uint256), `quantity` (uint256), `expiration` (uint256), `nonce` (uint256),
     - `questionId` (bytes32), `feeRateBps` (uint256), `intent` (uint8), `signatureType` (uint8)

## Where to Get This

- **Backend team:** Ask for the EIP-712 domain and struct used for order signature verification (code or spec).
- **Working Postman:** If place-order works in Postman, check how the signature is produced:
  - Which URL is called to get the signature (same sig-server or another)?
  - If another service/script is used, copy its domain and Order struct into `sig-server/signatures/server.js`.

## Current sig-server Definition

- **Domain:** `name`, `version`, `chainId`, `verifyingContract` (ethers.js uses this order).
- **Types:** `Order` with fields in order: salt, maker, signer, taker, price, quantity, expiration, nonce, questionId, feeRateBps, intent, signatureType (no timestamp).
- **Config:** `config.js` has `DOMAIN_NAME`, `DOMAIN_VERSION`, `CHAIN_ID`, `VERIFYING_CONTRACT`.

**Data types:** All uint256 in the signed message are strings; questionId is normalized to lowercase hex. Place-order API body still sends `timestamp` (Unix seconds) for the request; the signed Order struct does not include it.

Once you have the backend’s definition, update `sig-server/signatures/server.js` (domain, `types.Order` field order, and primary type name if different) so they match exactly; then the test will pass.

---

## Fix applied (UAT)

The EIP-712 message signed in sig-server did not match what the backend hashes for verification. The following changes aligned sig-server with the frontend/backend.

### 1. EIP-712 domain in sig-server (`sig-server/signatures/server.js`)

- Use **chainId as string** so it matches the frontend/backend: `chainId: String(config.CHAIN_ID)` (i.e. `"84532"`). Using a number produced a different domain separator and wrong recovered address.

### 2. UAT verifying contract (`sig-server/config.js`)

- Set `VERIFYING_CONTRACT` to the frontend constant: `0x398e870065121Ee3b0565e2b925cBD3f25df2Ce1` (fixed for UAT, no env override).

### 3. Alignment with frontend EIP-712 payload

- **Domain:** `name: "Pred CTF Exchange"`, `version: "1"`, `chainId: "84532"`, `verifyingContract: "0x398e870065121Ee3b0565e2b925cBD3f25df2Ce1"`.
- **Order:** 12 fields, same order and types: salt, maker, signer, taker, price, quantity, expiration, nonce, questionId, feeRateBps, intent, signatureType.
- **Message:** price/quantity/expiration/nonce/salt as strings; feeRateBps/intent as numbers; signatureType 2.

### Status and next steps

- Sig-server EIP-712 domain and Order signing match the frontend payload.
- **Confirm:** Run full suite with sig-server up and UAT reachable.
- **If tests fail elsewhere:** Ensure domain and verifying contract match that environment (e.g. different `VERIFYING_CONTRACT` for prod).

### Key files

| Area | File |
|------|------|
| Signing | `sig-server/signatures/server.js` (domain, orderFields, `/sign-order` message) |
| Config | `sig-server/config.js` (DOMAIN_*, CHAIN_ID, VERIFYING_CONTRACT) |
| Test | `src/test/java/com/pred/apitests/test/OrderTest.java` (`placeOrder_validSignature_accepted`) |
| Request models | `SignOrderRequest.java`, `PlaceOrderRequest.java` |

Optional: add two frontend EIP-712 payload examples here as reference when available.
