/**
 * In-memory wallet registry for load tests: opaque signing IDs map to ethers.Wallet instances.
 * Keys never leave this process except via explicit opt-in responses (see SIG_SERVER_EXPOSE_SECRETS).
 */
const crypto = require("crypto");
const { ethers } = require("ethers");

function normalizePrivateKey(pk) {
  if (!pk || typeof pk !== "string") return null;
  const t = pk.trim();
  if (!t) return null;
  const hex = t.startsWith("0x") ? t.slice(2) : t;
  if (!/^[0-9a-fA-F]{64}$/.test(hex)) {
    throw new Error("invalid private key: expected 32-byte hex");
  }
  return "0x" + hex.toLowerCase();
}

function newSigningId() {
  return "wk_" + crypto.randomBytes(16).toString("hex");
}

class WalletRegistry {
  constructor() {
    /** @type {Map<string, import('ethers').Wallet>} */
    this._byId = new Map();
  }

  has(signingId) {
    return signingId && this._byId.has(signingId);
  }

  /**
   * @param {string} signingId
   * @returns {import('ethers').Wallet | null}
   */
  getWallet(signingId) {
    if (!signingId || typeof signingId !== "string") return null;
    return this._byId.get(signingId) || null;
  }

  /**
   * Register an existing hex private key. Returns public metadata only.
   */
  registerPrivateKey(privateKeyHex) {
    const pk = normalizePrivateKey(privateKeyHex);
    const w = new ethers.Wallet(pk);
    const signingId = newSigningId();
    this._byId.set(signingId, w);
    return { signingId, address: w.address };
  }

  /**
   * Create a random wallet and register it.
   */
  registerRandom() {
    const w = ethers.Wallet.createRandom();
    const signingId = newSigningId();
    this._byId.set(signingId, w);
    return { signingId, address: w.address, wallet: w };
  }

  remove(signingId) {
    return this._byId.delete(signingId);
  }

  get size() {
    return this._byId.size;
  }
}

module.exports = {
  WalletRegistry,
  normalizePrivateKey,
  newSigningId,
};
