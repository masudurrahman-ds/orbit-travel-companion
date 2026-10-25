'use strict';
const http = require('node:http');
const { createApp } = require('../src/server');
const { Store } = require('../src/store');

async function start(opts = {}) {
  const env = { ORBIT_SECRET: 'test-secret', ORBIT_DEV_OTP: '1', ORBIT_ADMIN_TOKEN: 'admin-test-token', ...(opts.env || {}) };
  const store = new Store(null);
  const server = http.createServer(createApp({ store, env, fetchImpl: opts.fetchImpl }));
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, path, body, token, headers = {}) => {
    const res = await fetch(base + path, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined });
    const text = await res.text();
    let json; try { json = JSON.parse(text); } catch { json = text; }
    return { status: res.status, body: json };
  };
  const signup = async (identifier, name, consent = {}) => {
    const s = await call('POST', '/api/auth/signup', { identifier, password: 'correct-horse-9', displayName: name, consent: { terms: true, privacy: true, ...consent } });
    const v = await call('POST', '/api/auth/verify', { identifier, code: s.body.devCode });
    return v.body.token;
  };
  return { call, signup, store, close: () => server.close(), server };
}
module.exports = { start };
