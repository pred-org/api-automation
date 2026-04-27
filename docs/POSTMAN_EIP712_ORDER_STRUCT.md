# EIP-712 struct for Place Order (use in Postman)

Use this exact domain and Order struct in Postman to sign the place-order message. If this works in Postman, the backend matches; if not, the backend uses a different struct.

---

## 1. Domain (EIP712Domain)

| Field | Type | Value |
|-------|------|--------|
| name | string | `Pred CTF Exchange` |
| version | string | `1` |
| chainId | uint256 | `84532` |
| verifyingContract | address | `0xc76DC1c16E672f04b8faD103269E7474FA620a7B` |

Domain field order (for hashing): **name, version, chainId, verifyingContract**.

---

## 2. Primary type and Order struct

**Primary type:** `Order`

**Order** struct (field order matters):

| Field | Type |
|-------|------|
| salt | uint256 |
| maker | address |
| signer | address |
| taker | address |
| price | uint256 |
| quantity | uint256 |
| expiration | uint256 |
| nonce | uint256 |
| questionId | bytes32 |
| feeRateBps | uint256 |
| intent | uint8 |
| signatureType | uint8 |

---

## 3. Message (value to sign)

Use the **same** values in the same order. Example (replace with your variables):

```json
{
  "salt": "1772440065213",
  "maker": "0x54c7d8B0808357486Ef6bddE7475e06146B3fa90",
  "signer": "0x0d248E2d6cD3E815d64274d5B3F37771588285B6",
  "taker": "0x0000000000000000000000000000000000000000",
  "price": "30000000",
  "quantity": "100000000",
  "expiration": "0",
  "nonce": "0",
  "questionId": "0x0b5e4867f18d734efa40ff8c9144393e0fda072f90d865a2a8b086c02ccbb900",
  "feeRateBps": 0,
  "intent": 0,
  "signatureType": 2
}
```

Notes:
- **salt**: uint256 as decimal string (e.g. from `Date.now()` or your Postman salt).
- **maker**: proxy wallet address (checksum).
- **signer**: EOA address (checksum), must be the signer of the message.
- **price**: human price 30 -> wei with 6 decimals = `30 * 1e6` = **30000000**.
- **quantity**: human 100 -> wei with 6 decimals = `100 * 1e6` = **100000000**.
- **questionId**: market_id as bytes32 (64 hex chars), e.g. `0x0b5e4867f18d734efa40ff8c9144393e0fda072f90d865a2a8b086c02ccbb900`.

---

## 4. Postman Pre-request script (ethers v6 style)

If you sign in Postman with a script, the typed data payload should look like this (pseudo-code):

```javascript
const domain = {
  name: "Pred CTF Exchange",
  version: "1",
  chainId: 84532,
  verifyingContract: "0xc76DC1c16E672f04b8faD103269E7474FA620a7B"
};

const types = {
  Order: [
    { name: "salt", type: "uint256" },
    { name: "maker", type: "address" },
    { name: "signer", type: "address" },
    { name: "taker", type: "address" },
    { name: "price", type: "uint256" },
    { name: "quantity", type: "uint256" },
    { name: "expiration", type: "uint256" },
    { name: "nonce", type: "uint256" },
    { name: "questionId", type: "bytes32" },
    { name: "feeRateBps", type: "uint256" },
    { name: "intent", type: "uint8" },
    { name: "signatureType", type: "uint8" }
  ]
};

const message = {
  salt: pm.environment.get("salt"),           // or timestamp/salt variable
  maker: pm.environment.get("proxy_address"), // proxy wallet
  signer: pm.environment.get("eoa_address"),   // your EOA
  taker: "0x0000000000000000000000000000000000000000",
  price: "30000000",
  quantity: "100000000",
  expiration: "0",
  nonce: "0",
  questionId: pm.environment.get("market_id"),
  feeRateBps: 0,
  intent: 0,
  signatureType: 2
};

// Then use wallet.signTypedData(domain, types, message) and set the result as signature.
```

---

## 5. Place-order request body (API)

The **body** you send to the place-order API must use the **same** salt and timestamp (and price/quantity in human form). Example:

- `salt`: same value you used in the message (e.g. `"1772440065213"`).
- `timestamp`: Unix seconds (e.g. `1772440065`).
- `price`: `"30"`, `quantity`: `"100"`, `amount`: `"30"`.
- `market_id`: same as questionId (e.g. `0x0b5e4867...`).
- `signature`: the hex string returned from signTypedData (0x + 130 hex chars).

If signing in Postman with this struct gives a signature that the API accepts, then the backend matches this EIP-712 definition and we can keep sig-server aligned with it.
