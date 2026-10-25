'use strict';
const crypto = require('node:crypto');

const TERMS_VERSION = '2026-10-01';
const PRIVACY_VERSION = '2026-10-01';

const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;

/** Returns { type, value } or null. Bangladesh mobile numbers are normalised to +8801XXXXXXXXX. */
function normalizeIdentifier(input) {
  if (typeof input !== 'string') return null;
  const s = input.trim();
  if (EMAIL_RE.test(s) && s.length <= 254) return { type: 'email', value: s.toLowerCase() };
  const digits = s.replace(/[\s()-]/g, '');
  let m = digits.match(/^(?:\+?88)?(01[3-9]\d{8})$/);
  if (m) return { type: 'phone', value: '+88' + m[1] };
  return null;
}

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, 64);
  return salt.toString('hex') + ':' + hash.toString('hex');
}

function verifyPassword(password, stored) {
  const [saltHex, hashHex] = String(stored).split(':');
  if (!saltHex || !hashHex) return false;
  const expected = Buffer.from(hashHex, 'hex');
  const actual = crypto.scryptSync(password, Buffer.from(saltHex, 'hex'), expected.length);
  return crypto.timingSafeEqual(actual, expected);
}

function passwordProblem(password, identifier) {
  if (typeof password !== 'string' || password.length < 8) return 'Password must be at least 8 characters.';
  if (password.length > 128) return 'Password is too long.';
  if (password.toLowerCase() === String(identifier).toLowerCase()) return 'Password must differ from your email or number.';
  return null;
}

const b64 = (buf) => Buffer.from(buf).toString('base64url');

function signToken(payload, secret, ttlSeconds = 7 * 24 * 3600) {
  const body = b64(JSON.stringify({ ...payload, exp: Math.floor(Date.now() / 1000) + ttlSeconds }));
  const sig = b64(crypto.createHmac('sha256', secret).update(body).digest());
  return body + '.' + sig;
}

function verifyToken(token, secret) {
  if (typeof token !== 'string' || !token.includes('.')) return null;
  const [body, sig] = token.split('.');
  const expected = crypto.createHmac('sha256', secret).update(body).digest();
  let given;
  try { given = Buffer.from(sig, 'base64url'); } catch { return null; }
  if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) return null;
  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
    return payload.exp > Math.floor(Date.now() / 1000) ? payload : null;
  } catch { return null; }
}

function newOtp() {
  const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
  return { code, hash: crypto.createHash('sha256').update(code).digest('hex'), expiresAt: Date.now() + 10 * 60 * 1000, attempts: 0 };
}
function checkOtp(record, code) {
  if (!record || Date.now() > record.expiresAt || record.attempts >= 5) return false;
  record.attempts += 1;
  const h = crypto.createHash('sha256').update(String(code)).digest('hex');
  return crypto.timingSafeEqual(Buffer.from(h), Buffer.from(record.hash));
}

/** Sliding-window limiter kept in memory. */
class RateLimiter {
  constructor(max, windowMs) { this.max = max; this.windowMs = windowMs; this.hits = new Map(); }
  allow(key) {
    const now = Date.now();
    const arr = (this.hits.get(key) || []).filter((t) => now - t < this.windowMs);
    if (arr.length >= this.max) { this.hits.set(key, arr); return false; }
    arr.push(now); this.hits.set(key, arr); return true;
  }
}

module.exports = {
  TERMS_VERSION, PRIVACY_VERSION, normalizeIdentifier, hashPassword, verifyPassword, passwordProblem,
  signToken, verifyToken, newOtp, checkOtp, RateLimiter,
};
