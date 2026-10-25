'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { start } = require('./helpers');
const { computeBadges } = require('../src/badges');
const groups = require('../src/groups');
const prices = require('../src/prices');
const { BASE_DESTINATIONS: D } = require('../src/data');

const spot = (name) => D.find((d) => d.name === name);

test('GPS check-ins are verified only near the spot, never for mock locations, and never downgraded', async () => {
  const t = await start();
  const u = await t.signup('v@example.com', 'Vic', { leaderboard: true });
  const jaflong = spot('Jaflong');
  const near = await t.call('POST', '/api/visits', { destinationId: jaflong.id, source: 'gps', lat: jaflong.latitude + 0.02, lon: jaflong.longitude }, u);
  assert.equal(near.body.visits[0].verified, true);
  const far = await t.call('POST', '/api/visits', { destinationId: spot('Sajek Valley').id, source: 'gps', lat: 23.8, lon: 90.4 }, u);
  assert.equal(far.body.visits.find((v) => v.destinationId === spot('Sajek Valley').id).verified, false);
  assert.equal(far.body.visits.find((v) => v.destinationId === spot('Sajek Valley').id).source, 'self');
  const mock = await t.call('POST', '/api/visits', { destinationId: spot('Kaptai Lake').id, source: 'gps', mock: true, lat: spot('Kaptai Lake').latitude, lon: spot('Kaptai Lake').longitude }, u);
  assert.equal(mock.body.visits.find((v) => v.destinationId === spot('Kaptai Lake').id).verified, false);
  const noCoords = await t.call('POST', '/api/visits', { destinationId: spot('Nilgiri Hills').id, source: 'gps' }, u);
  assert.equal(noCoords.body.visits.find((v) => v.destinationId === spot('Nilgiri Hills').id).verified, false);
  const again = await t.call('POST', '/api/visits', { destinationId: jaflong.id, rating: 5 }, u);
  assert.equal(again.body.visits.find((v) => v.destinationId === jaflong.id).verified, true, 'a later manual edit does not remove verification');
  t.close();
});

test('leaderboard ranks verified check-ins first and shows the verified count', async () => {
  const t = await start();
  const a = await t.signup('a2@example.com', 'Ann', { leaderboard: true });
  const b = await t.signup('b2@example.com', 'Ben', { leaderboard: true });
  for (const n of ['Jaflong', 'Sajek Valley', 'Kaptai Lake']) await t.call('POST', '/api/visits', { destinationId: spot(n).id }, a);
  const j = spot('Jaflong');
  await t.call('POST', '/api/visits', { destinationId: j.id, source: 'gps', lat: j.latitude, lon: j.longitude }, b);
  const lb = (await t.call('GET', '/api/leaderboard', null, a)).body;
  assert.deepEqual(lb.top.map((r) => r.displayName), ['Ben', 'Ann']);
  assert.equal(lb.top[0].verified, 1);
  assert.equal(lb.me.verified, 0);
  t.close();
});

test('badges: progress, earning and one row per spot', () => {
  const sylhet = D.filter((d) => d.district === 'Sylhet');
  const none = computeBadges(D, []);
  assert.ok(none.every((b) => !b.earned && b.progress === 0));
  const some = computeBadges(D, [{ destinationId: sylhet[0].id }, { destinationId: sylhet[0].id }, { destinationId: sylhet[1].id, note: 'lovely', source: 'gps' }]);
  const by = Object.fromEntries(some.map((b) => [b.id, b]));
  assert.equal(by.first_step.earned, true);
  assert.equal(by.explorer_10.progress, 2, 'duplicate rows count once');
  assert.equal(by.district_5.progress, 1);
  assert.equal(by.storyteller.progress, 1);
  assert.equal(by.verified_5.progress, 1);
  const all = computeBadges(D, D.map((d) => ({ destinationId: d.id, note: 'x', source: 'gps' })));
  assert.ok(all.every((b) => b.earned), 'visiting everything earns every badge');
  const divisions = new Set(D.map((d) => d.division));
  const perDivision = [...divisions].map((div) => ({ destinationId: D.find((d) => d.division === div).id }));
  assert.equal(computeBadges(D, perDivision).find((b) => b.id === 'all_divisions').earned, true);
  assert.equal(divisions.size, 8);
});

test('badges endpoint', async () => {
  const t = await start();
  const u = await t.signup('b3@example.com', 'Bea');
  await t.call('POST', '/api/visits', { destinationId: 1 }, u);
  const r = await t.call('GET', '/api/badges', null, u);
  assert.equal(r.body.badges.find((b) => b.id === 'first_step').earned, true);
  t.close();
});

test('equalShares always adds up and spreads leftovers', () => {
  assert.deepEqual(groups.equalShares(100, ['a', 'b', 'c']).map((s) => s.share), [34, 33, 33]);
  assert.equal(groups.equalShares(1, ['a', 'b', 'c']).reduce((s, x) => s + x.share, 0), 1);
  assert.equal(groups.equalShares(7, ['a']).reduce((s, x) => s + x.share, 0), 7);
});

test('balances and settlements: simple case', () => {
  const ids = ['a', 'b', 'c'];
  const expenses = [{ paidBy: 'a', amount: 300, shares: groups.equalShares(300, ids) }];
  const net = groups.balances(ids, expenses);
  assert.deepEqual(net, { a: 200, b: -100, c: -100 });
  const tx = groups.settlements(net);
  assert.deepEqual(tx.map((t) => [t.from, t.to, t.amount]).sort(), [['b', 'a', 100], ['c', 'a', 100]]);
});

test('settlements: fuzz test, net always zero and fully cleared in at most n-1 transfers', () => {
  let seed = 12345; const rnd = (n) => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed % n; };
  for (let round = 0; round < 300; round++) {
    const n = 2 + rnd(7), ids = Array.from({ length: n }, (_, i) => 'm' + i);
    const expenses = [];
    for (let k = 0; k < 1 + rnd(12); k++) {
      const amount = 1 + rnd(50000), split = ids.filter(() => rnd(3) > 0); const sp = split.length ? split : [ids[0]];
      expenses.push({ paidBy: ids[rnd(n)], amount, shares: groups.equalShares(amount, sp) });
    }
    const net = groups.balances(ids, expenses);
    assert.equal(Object.values(net).reduce((s, v) => s + v, 0), 0, 'money is conserved');
    const tx = groups.settlements(net);
    assert.ok(tx.length <= n - 1);
    const after = { ...net };
    for (const t of tx) { assert.ok(t.amount > 0); after[t.from] += t.amount; after[t.to] -= t.amount; }
    assert.ok(Object.values(after).every((v) => v === 0), 'everyone is square after the transfers');
  }
});

test('group API: create, join, expenses, settle, permissions, leave', async () => {
  const t = await start();
  const ann = await t.signup('g1@example.com', 'Ann'), ben = await t.signup('g2@example.com', 'Ben'), cat = await t.signup('g3@example.com', 'Cat');
  const created = (await t.call('POST', '/api/groups', { name: 'Sajek crew' }, ann)).body.group;
  assert.match(created.code, /^[A-HJ-NP-Z2-9]{6}$/);
  assert.equal((await t.call('POST', '/api/groups/join', { code: 'ZZZZZZ' }, ben)).status, 404);
  const joined = (await t.call('POST', '/api/groups/join', { code: created.code.toLowerCase() }, ben)).body.group;
  assert.equal(joined.members.length, 2);
  await t.call('POST', '/api/groups/join', { code: created.code }, cat);
  const g = (await t.call('GET', '/api/groups/' + created.id, null, ann)).body.group;
  const mid = (name) => g.members.find((m) => m.displayName === name).mid;
  assert.equal((await t.call('POST', `/api/groups/${created.id}/expenses`, { description: 'Boat', amount: -5, paidBy: mid('Ann') }, ann)).status, 400);
  assert.equal((await t.call('POST', `/api/groups/${created.id}/expenses`, { description: 'Boat', amount: 900, paidBy: 'nope' }, ann)).status, 400);
  const e1 = (await t.call('POST', `/api/groups/${created.id}/expenses`, { description: 'Boat', amount: 900, paidBy: mid('Ann') }, ann)).body.group;
  assert.equal(e1.total, 900);
  assert.deepEqual(e1.settlements.map((s) => [s.fromName, s.toName, s.amount]).sort(), [['Ben', 'Ann', 300], ['Cat', 'Ann', 300]]);
  const e2 = (await t.call('POST', `/api/groups/${created.id}/expenses`, { description: 'Dinner', amount: 400, paidBy: mid('Ben'), split: [mid('Ben'), mid('Cat')] }, ben)).body.group;
  assert.equal(e2.balances.reduce((s, b) => s + b.amount, 0), 0);
  const outsider = await t.signup('g4@example.com', 'Out');
  assert.equal((await t.call('GET', '/api/groups/' + created.id, null, outsider)).status, 404, 'non-members cannot see the group');
  assert.equal((await t.call('DELETE', `/api/groups/${created.id}/expenses/${e2.expenses[1].id}`, null, cat)).status, 403);
  assert.equal((await t.call('DELETE', `/api/groups/${created.id}/expenses/${e2.expenses[1].id}`, null, ben)).status, 200);
  const plan = await t.call('POST', `/api/groups/${created.id}/plan`, { plan: { destinationName: 'Sajek Valley', days: 3, tier: 'budget', items: [{ day: 1, hour: 9, name: 'Sunrise viewpoint' }] } }, cat);
  assert.equal(plan.body.group.plan.sharedBy, 'Cat');
  assert.equal((await t.call('POST', `/api/groups/${created.id}/leave`, null, cat)).status, 200);
  assert.equal((await t.call('GET', '/api/groups/' + created.id, null, cat)).status, 404);
  const after = (await t.call('GET', '/api/groups/' + created.id, null, ann)).body.group;
  assert.ok(after.members.some((m) => m.displayName === 'Former member' && m.left));
  assert.equal((await t.call('GET', '/api/groups', null, ann)).body.groups[0].memberCount, 2);
  t.close();
});

test('deleting an account anonymises its group membership and removes its price reports', async () => {
  const t = await start();
  const ann = await t.signup('d1@example.com', 'Ann'), ben = await t.signup('d2@example.com', 'Ben');
  const g = (await t.call('POST', '/api/groups', { name: 'Trip' }, ann)).body.group;
  await t.call('POST', '/api/groups/join', { code: g.code }, ben);
  await t.call('POST', '/api/prices', { destinationId: 1, kind: 'bus', amountBdt: 800 }, ben);
  assert.equal((await t.call('DELETE', '/api/me', null, ben)).status, 200);
  const view = (await t.call('GET', '/api/groups/' + g.id, null, ann)).body.group;
  assert.ok(view.members.some((m) => m.left && m.displayName === 'Former member'));
  assert.ok(!JSON.stringify(view).includes('Ben'));
  assert.deepEqual((await t.call('GET', '/api/prices')).body.prices, {});
  t.close();
});

test('robustMedian ignores wild values once there are enough reports', () => {
  assert.equal(prices.robustMedian([100, 200, 300]), 200);
  assert.equal(prices.robustMedian([100, 200]), 150);
  assert.equal(prices.robustMedian([800, 850, 900, 950, 1000, 900, 880, 910, 890, 99999]), 900);
});

test('price reports: validation, latest-per-traveller wins, aggregation, public read', async () => {
  const t = await start();
  const a = await t.signup('p1@example.com', 'Pat'), b = await t.signup('p2@example.com', 'Pam');
  assert.equal((await t.call('POST', '/api/prices', { destinationId: 1, kind: 'bus', amountBdt: 800 })).status, 401);
  assert.equal((await t.call('POST', '/api/prices', { destinationId: 99999, kind: 'bus', amountBdt: 800 }, a)).status, 400);
  assert.equal((await t.call('POST', '/api/prices', { destinationId: 1, kind: 'rocket', amountBdt: 800 }, a)).status, 400);
  assert.equal((await t.call('POST', '/api/prices', { destinationId: 1, kind: 'bus', amountBdt: 0 }, a)).status, 400);
  assert.equal((await t.call('POST', '/api/prices', { destinationId: 1, kind: 'bus', amountBdt: 12.5 }, a)).status, 400);
  await t.call('POST', '/api/prices', { destinationId: 1, kind: 'bus', amountBdt: 500 }, a);
  await t.call('POST', '/api/prices', { destinationId: 1, kind: 'bus', amountBdt: 900 }, a);
  await t.call('POST', '/api/prices', { destinationId: 1, kind: 'bus', amountBdt: 1100 }, b);
  const agg = (await t.call('GET', '/api/prices')).body.prices;
  assert.equal(agg['1'].bus.count, 2);
  assert.equal(agg['1'].bus.median, 1000);
  t.close();
});

test('advisories: operator only, validated, shown while active, hidden once expired', async () => {
  const t = await start();
  const admin = { 'x-admin-token': 'admin-test-token' };
  const good = { scope: 'district', target: 'Rangamati', severity: 'warning', title: 'Landslide risk', text: 'Heavy rain. Check local advice before travelling.', hours: 24 };
  assert.equal((await t.call('POST', '/api/admin/advisories', good)).status, 403);
  assert.equal((await t.call('POST', '/api/admin/advisories', good, null, { 'x-admin-token': 'wrong' })).status, 403);
  assert.equal((await t.call('POST', '/api/admin/advisories', { ...good, target: 'Atlantis' }, null, admin)).status, 400);
  assert.equal((await t.call('POST', '/api/admin/advisories', { ...good, severity: 'meh' }, null, admin)).status, 400);
  assert.equal((await t.call('POST', '/api/admin/advisories', { ...good, scope: 'planet' }, null, admin)).status, 400);
  assert.equal((await t.call('POST', '/api/admin/advisories', good, null, admin)).status, 201);
  assert.equal((await t.call('POST', '/api/admin/advisories', { scope: 'national', severity: 'info', title: 'Eid travel rush', text: 'Expect crowded buses.' }, null, admin)).status, 201);
  assert.equal((await t.call('GET', '/api/advisories')).body.advisories.length, 2);
  t.store.db.advisories[0].expiresAt = new Date(Date.now() - 1000).toISOString();
  assert.deepEqual((await t.call('GET', '/api/advisories')).body.advisories.map((a) => a.title), ['Eid travel rush']);
  assert.equal((await t.call('DELETE', '/api/admin/advisories/' + t.store.db.advisories[1].id, null, null, admin)).status, 200);
  assert.equal((await t.call('GET', '/api/advisories')).body.advisories.length, 0);
  t.close();
});
