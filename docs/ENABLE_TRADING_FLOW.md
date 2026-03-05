# Enable trading flow

## Overview

Enable trading is a 3-step flow: **prepare** (backend builds Safe tx), **sign** (client signs the tx hash), **execute** (backend submits the tx). The proxy (Safe) must already exist on chain; otherwise prepare fails with "no contract code at given address".

## Flow (test order)

1. **Auth (AuthFlowTest.setupSuite)**  
   - Create or use API key.  
   - Get CreateProxy signature from sig-server using **your wallet** (private key from config).  
   - Login with that signature; backend returns `access_token`, `user_id`, `proxy_wallet_address`.  
   - Store in TokenManager: token, refresh cookie, userId, **proxy**, **EOA**, **privateKey**.

2. **Enable trading prepare**  
   - `POST /api/v1/user/safe-approval/prepare`  
   - Body: `{ "proxy_wallet_address": "<from login>" }`  
   - Headers: `Authorization: Bearer <access_token>`, `Cookie: refresh_token=...`  
   - Backend reads the Safe nonce at that address and returns a Safe tx payload and **transactionHash**.

3. **Sign transactionHash**  
   - Call sig-server `POST /sign-safe-approval` with `transactionHash` and **same wallet privateKey** (EOA that owns the Safe).  
   - Returns signature (raw hash sign, not personal_sign).

4. **Enable trading execute**  
   - `POST /api/v1/user/safe-approval/execute`  
   - Body: `{ "data": <prepare payload>, "signature": "<from step 3>" }`  
   - Same auth headers.  
   - Backend submits the Safe tx on chain.

## What we use from config

- **private.key / eoa.address** (testdata.properties or env): used for sign-create-proxy (login) and sign-safe-approval (enable-trading). Same wallet must own the Safe.  
- **proxy_wallet_address**: from login response only; we do not derive it.

## Why it works for old user but not new user

- **Old user**: Proxy (Safe) was already deployed on chain (e.g. from earlier login or manual deploy). Prepare finds contract at that address and succeeds.  
- **New user**: Backend may return a **deterministic proxy address** (e.g. Create2) at login before the Safe is actually deployed. Prepare then tries to read nonce at that address and gets "no contract code at given address" because deployment is not done yet (or is async).

So the test flow is correct; the failure is that for a **first-time user** the Safe may not be deployed at the proxy address when we call prepare immediately after login. Backend should either deploy the Safe synchronously at login or ensure prepare waits for deployment / retries.

## What to report to dev team

- Endpoint: `POST /api/v1/user/safe-approval/prepare`  
- Error: 500, "failed to prepare safe approval: ... read nonce: no contract code at given address"  
- Body sent: `{ "proxy_wallet_address": "<value from login>" }`  
- Ask: For a user that just logged in (new proxy), is the Safe deployed on Base Sepolia before we call prepare? If deployment is async, prepare will fail until the contract exists.
