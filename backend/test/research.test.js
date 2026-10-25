'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { start } = require('./helpers');
const { COSTS, estimateTransport, PER_KM } = require('../src/transport');
const { BASE_DESTINATIONS: D } = require('../src/data');

test('every researched entry is dated, sourced and rated for confidence', () => {
  const ids = new Set();
  for (const e of COSTS.entries) {
    assert.ok(!ids.has(e.id), `duplicate id ${e.id}`); ids.add(e.id);
    assert.ok(['transport', 'food', 'stay', 'package', 'access', 'safety'].includes(e.category), e.id);
    assert.ok(e.subject && e.value && e.asOf && e.note, `${e.id} is missing a field`);
    assert.ok(['H', 'M', 'L'].includes(e.confidence), e.id);
    assert.match(e.url, /^https:\/\//, e.id);
    assert.ok(e.source && e.source.length > 3, e.id);
  }
  assert.ok(COSTS.entries.length >= 20);
});

test('fares are internally consistent: lower seat count costs less, ranges are ordered, hike is 17 paisa', () => {
  for (const e of COSTS.entries.filter((x) => x.id.startsWith('route_') && x.value.bus_nonac_tk)) {
    assert.ok(e.value.bus_nonac_tk.seats51 < e.value.bus_nonac_tk.seats40, e.id);
  }
  for (const e of COSTS.entries) for (const [k, v] of Object.entries(e.value)) if (k.endsWith('range') || k.endsWith('_range_tk') || k === 'tk_range') assert.ok(Array.isArray(v) && v[0] <= v[1], `${e.id}.${k}`);
  assert.ok(Math.abs(PER_KM.high - PER_KM.low - 0.17) < 1e-9);
  const cx = COSTS.entries.find((e) => e.id === 'route_dhaka_coxsbazar').value;
  assert.ok(cx.train_tk.snigdha < cx.train_tk.ac_seat && cx.train_tk.ac_seat < cx.train_tk.ac_berth);
});

test('BRTA fares imply road distances that match ORBIT distances for the routes checked', () => {
  const implied = (id) => COSTS.entries.find((e) => e.id === id).value.bus_nonac_tk.seats51 / PER_KM.low;
  const within = (id, district, pct) => {
    const seed = D.find((d) => d.district === district).distanceKm;
    assert.ok(Math.abs(implied(id) - seed) / seed <= pct, `${district}: implied ${implied(id).toFixed(0)} km vs seed ${seed} km`);
  };
  within('route_dhaka_coxsbazar', "Cox's Bazar", 0.05);
  within('route_dhaka_chattogram', 'Chattogram', 0.05);
  within('route_dhaka_mymensingh', 'Mymensingh', 0.12);
  // Known outlier, recorded in the audit for verification rather than silently "fixed".
  const r = implied('route_dhaka_rajshahi'), s = D.find((d) => d.district === 'Rajshahi').distanceKm;
  assert.ok(Math.abs(r - s) / s > 0.2, 'Rajshahi still flagged as inconsistent');
});

test('transport estimate uses sourced fares where they exist and labels estimates elsewhere', () => {
  const cox = estimateTransport(D.find((d) => d.name === "Cox's Bazar Beach"));
  assert.equal(cox.busNonAc.oneWayTk.seats51, 900);
  assert.equal(cox.train.oneWayTk.ac_berth, 2656);
  assert.ok(cox.air.oneWayFromTk >= 4000);
  const jaflong = estimateTransport(D.find((d) => d.name === 'Jaflong'));
  assert.ok(jaflong.busNonAc.oneWayTkEstimate.low < jaflong.busNonAc.oneWayTkEstimate.high);
  assert.equal(jaflong.busNonAc.confidence, 'L');
  assert.match(jaflong.busNonAc.basis, /BRTA/);
  assert.ok(jaflong.notes.some((n) => /AC and premium/.test(n)));
  assert.match(estimateTransport(D.find((d) => d.district === 'Dhaka')).notes[0], /Within Dhaka/);
  const far = estimateTransport(D.find((d) => d.district === 'Panchagarh'));
  assert.ok(far.busNonAc.oneWayTk.seats51 > 900);
});

test('Saint Martin best months follow the 2025-26 access rules', () => {
  const sm = D.find((d) => d.name === "Saint Martin's Island");
  assert.deepEqual(sm.bestMonths, [11, 12, 1]);
  assert.ok(!sm.bestMonths.includes(3));
});

test('reference data endpoints', async () => {
  const t = await start();
  const all = await t.call('GET', '/api/travel-costs');
  assert.equal(all.status, 200); assert.ok(all.body.entries.length >= 20); assert.ok(all.body.meta.warning);
  const cox = D.find((d) => d.name === "Cox's Bazar Beach");
  const r = await t.call('GET', '/api/transport/' + cox.id);
  assert.equal(r.status, 200); assert.equal(r.body.busNonAc.oneWayTk.seats51, 900);
  assert.equal((await t.call('GET', '/api/transport/999999')).status, 404);
  t.close();
});

test('the researched advisories are valid and the server accepts every one', async () => {
  const list = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'advisories.seed.json'), 'utf8'));
  assert.ok(list.length >= 6);
  const t = await start();
  for (const a of list) {
    const r = await t.call('POST', '/api/admin/advisories', a, null, { 'x-admin-token': 'admin-test-token' });
    assert.equal(r.status, 201, `${a.title}: ${JSON.stringify(r.body)}`);
  }
  assert.equal((await t.call('GET', '/api/advisories')).body.advisories.length, list.length);
  t.close();
});
