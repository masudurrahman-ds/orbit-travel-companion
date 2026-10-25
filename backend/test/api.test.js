'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { start } = require('./helpers');

test('signup requires both agreements', async () => {
  const t = await start();
  const r = await t.call('POST', '/api/auth/signup', { identifier: 'a@example.com', password: 'correct-horse-9', displayName: 'Ann', consent: { terms: true, privacy: false } });
  assert.equal(r.status, 400); assert.equal(r.body.error, 'consent_required');
  t.close();
});

test('signup validates identifier and password', async () => {
  const t = await start();
  assert.equal((await t.call('POST', '/api/auth/signup', { identifier: 'nope', password: 'correct-horse-9', displayName: 'Ann', consent: { terms: true, privacy: true } })).body.error, 'invalid_identifier');
  assert.equal((await t.call('POST', '/api/auth/signup', { identifier: 'a@example.com', password: 'short', displayName: 'Ann', consent: { terms: true, privacy: true } })).body.error, 'weak_password');
  t.close();
});

test('email and phone signup, verify, login; login blocked before verification', async () => {
  const t = await start();
  const s = await t.call('POST', '/api/auth/signup', { identifier: '01712345678', password: 'correct-horse-9', displayName: 'Rafi', consent: { terms: true, privacy: true } });
  assert.equal(s.status, 201);
  const early = await t.call('POST', '/api/auth/login', { identifier: '+8801712345678', password: 'correct-horse-9' });
  assert.equal(early.status, 403);
  const bad = await t.call('POST', '/api/auth/verify', { identifier: '01712345678', code: '000000' });
  assert.equal(bad.status, 400);
  const ok = await t.call('POST', '/api/auth/verify', { identifier: '01712345678', code: s.body.devCode });
  assert.equal(ok.status, 200);
  const login = await t.call('POST', '/api/auth/login', { identifier: '+8801712345678', password: 'correct-horse-9' });
  assert.equal(login.status, 200);
  const wrong = await t.call('POST', '/api/auth/login', { identifier: '+8801712345678', password: 'wrong-password' });
  assert.equal(wrong.status, 401);
  assert.equal((await t.call('GET', '/api/me', null, login.body.token)).body.user.identifier, '+8801712345678');
  assert.equal((await t.call('GET', '/api/me')).status, 401);
  assert.equal((await t.call('GET', '/api/me', null, login.body.token + 'x')).status, 401);
  t.close();
});

test('verification attempts are throttled across re-issued codes', async () => {
  const t = await start();
  const signup = await t.call('POST', '/api/auth/signup', { identifier: 'verify-limit@example.com', password: 'correct-horse-9', displayName: 'Verifier', consent: { terms: true, privacy: true } });
  assert.equal(signup.status, 201);
  // The test helper uses one client IP, so the per-identifier/IP limiter is deterministic.
  const wrongCode = signup.body.devCode === '999999' ? '999998' : '999999';
  for (let i = 0; i < 10; i++) {
    const result = await t.call('POST', '/api/auth/verify', { identifier: 'verify-limit@example.com', code: wrongCode });
    assert.equal(result.status, 400);
  }
  const limited = await t.call('POST', '/api/auth/verify', { identifier: 'verify-limit@example.com', code: signup.body.devCode });
  assert.equal(limited.status, 429);
  t.close();
});

test('password hash is never returned', async () => {
  const t = await start();
  const token = await t.signup('p@example.com', 'Pat');
  const me = await t.call('GET', '/api/me', null, token);
  assert.ok(!JSON.stringify(me.body).includes('passwordHash'));
  t.close();
});

test('visits upsert, validate destination, and drive the leaderboard only for opted-in users', async () => {
  const t = await start();
  const a = await t.signup('a@example.com', 'Ann', { leaderboard: true });
  const b = await t.signup('b@example.com', 'Ben', { leaderboard: false });
  assert.equal((await t.call('POST', '/api/visits', { destinationId: 999999 }, a)).status, 400);
  await t.call('POST', '/api/visits', { destinationId: 1, rating: 5 }, a);
  await t.call('POST', '/api/visits', { destinationId: 1, rating: 4, note: 'again' }, a);
  const v = await t.call('POST', '/api/visits', { destinationId: 2 }, a);
  assert.equal(v.body.visits.length, 2);
  await t.call('POST', '/api/visits', { destinationId: 1 }, b);
  await t.call('POST', '/api/visits', { destinationId: 2 }, b);
  await t.call('POST', '/api/visits', { destinationId: 3 }, b);
  const lbA = await t.call('GET', '/api/leaderboard', null, a);
  assert.equal(lbA.body.top.length, 1);
  assert.equal(lbA.body.top[0].displayName, 'Ann'); assert.equal(lbA.body.me.spots, 2);
  assert.ok(!JSON.stringify(lbA.body).includes('a@example.com'));
  const lbB = await t.call('GET', '/api/leaderboard', null, b);
  assert.equal(lbB.body.optedIn, false); assert.equal(lbB.body.me, null);
  t.close();
});

test('community spots: validation, duplicate check, moderation', async () => {
  const t = await start();
  const u = await t.signup('c@example.com', 'Cam');
  const spot = { name: 'Hidden Lake View', district: 'Sylhet', tags: ['nature'], costPerDayBdt: 2000, description: 'Quiet lake. <script>x</script>' };
  assert.equal((await t.call('POST', '/api/destinations/submit', { ...spot, district: 'Atlantis' }, u)).body.error, 'invalid_district');
  assert.equal((await t.call('POST', '/api/destinations/submit', { ...spot, tags: ['spaceflight'] }, u)).body.error, 'invalid_tags');
  assert.equal((await t.call('POST', '/api/destinations/submit', { ...spot, latitude: 10, longitude: 10 }, u)).body.error, 'invalid_coordinates');
  assert.equal((await t.call('POST', '/api/destinations/submit', { ...spot, name: 'Jaflong' }, u)).body.error, 'duplicate');
  const ok = await t.call('POST', '/api/destinations/submit', spot, u);
  assert.equal(ok.status, 201);
  let list = (await t.call('GET', '/api/destinations')).body.destinations;
  assert.ok(!list.some((d) => d.name === 'Hidden Lake View'), 'pending spots stay hidden');
  assert.equal((await t.call('POST', '/api/admin/submissions/' + ok.body.id, { action: 'approve' }, null, { 'x-admin-token': 'wrong' })).status, 403);
  assert.equal((await t.call('POST', '/api/admin/submissions/' + ok.body.id, { action: 'approve' }, null, { 'x-admin-token': 'admin-test-token' })).status, 200);
  list = (await t.call('GET', '/api/destinations')).body.destinations;
  const added = list.find((d) => d.name === 'Hidden Lake View');
  assert.ok(added && added.community && added.id >= 10000);
  assert.ok(!added.blurb.includes('<'), 'markup stripped');
  t.close();
});

test('export, consent update and account deletion', async () => {
  const t = await start();
  const u = await t.signup('d@example.com', 'Dee', { leaderboard: true });
  await t.call('POST', '/api/visits', { destinationId: 1 }, u);
  const ex = await t.call('GET', '/api/me/export', null, u);
  assert.equal(ex.body.visits.length, 1);
  const c = await t.call('PUT', '/api/me/consent', { leaderboard: false, ai: true }, u);
  assert.equal(c.body.consent.leaderboard, false); assert.equal(c.body.consent.ai, true);
  assert.equal((await t.call('DELETE', '/api/me', null, u)).status, 200);
  assert.equal((await t.call('GET', '/api/me', null, u)).status, 401);
  assert.equal((await t.call('POST', '/api/auth/login', { identifier: 'd@example.com', password: 'correct-horse-9' })).status, 401);
  t.close();
});

test('privacy and terms pages are served', async () => {
  const t = await start();
  const p = await t.call('GET', '/privacy');
  assert.equal(p.status, 200); assert.ok(String(p.body).includes('Privacy Policy'));
  assert.equal((await t.call('GET', '/terms')).status, 200);
  t.close();
});
