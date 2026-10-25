'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const planner = require('../src/planner');
const { BASE_DESTINATIONS, ACTIVITIES, DISTRICTS } = require('../src/data');

test('every tier has weights that sum to 1', () => {
  for (const [name, t] of Object.entries(planner.TIERS)) {
    const sum = Object.values(t.weights).reduce((a, b) => a + b, 0);
    assert.ok(Math.abs(sum - 1) < 1e-9, name);
  }
});

test('data covers 64 districts and each spot has two indoor activities', () => {
  assert.equal(DISTRICTS.length, 64);
  assert.equal(new Set(BASE_DESTINATIONS.map((d) => d.district)).size, 64);
  for (const d of BASE_DESTINATIONS) assert.ok(ACTIVITIES.filter((a) => a.destinationId === d.id && a.isIndoor).length >= 2, d.name);
});

test('beach and food in December ranks Cox\'s Bazar first (value tier)', () => {
  const rows = planner.rank(BASE_DESTINATIONS, { budget: 30000, days: 3, travellers: 2, month: 12, interests: ['beach', 'food'], tier: 'value' });
  assert.equal(rows[0].destination.district, "Cox's Bazar");
});

test('budget tier costs less and fancy costs more than value for the same trip', () => {
  const d = BASE_DESTINATIONS.find((x) => x.name === "Cox's Bazar Beach");
  const q = { days: 3, travellers: 2 };
  const b = planner.estimateTrip(d, { ...q, tier: 'budget' }).totalBdt, v = planner.estimateTrip(d, { ...q, tier: 'value' }).totalBdt, f = planner.estimateTrip(d, { ...q, tier: 'fancy' }).totalBdt;
  assert.ok(b < v && v < f);
});

test('on a tight budget, affordable spots come first for budget and value tiers', () => {
  const q = { budget: 12000, days: 3, travellers: 2, month: 12, interests: ['nature'] };
  for (const tier of ['budget', 'value']) {
    const rows = planner.rank(BASE_DESTINATIONS, { ...q, tier });
    const firstUnaffordable = rows.findIndex((r) => !r.score.affordable);
    assert.ok(rows.slice(firstUnaffordable).every((r) => !r.score.affordable), tier);
  }
});

test('good community ratings lift a spot, with a Bayesian prior limiting one-off ratings', () => {
  const d = BASE_DESTINATIONS[0];
  const q = { budget: 30000, days: 3, travellers: 2, month: 12, interests: [], tier: 'value' };
  const base = planner.scoreDestination(d, q).total;
  const lifted = planner.scoreDestination(d, q, { quality: planner.qualityFromRatings(Array(30).fill(5)) }).total;
  const oneVote = planner.scoreDestination(d, q, { quality: planner.qualityFromRatings([5]) }).total;
  assert.ok(lifted > oneVote && oneVote > base - 1e-9);
});

test('itinerary: rainy day gets only indoor items; tiers order costs differently', () => {
  const cox = BASE_DESTINATIONS.find((d) => d.name === "Cox's Bazar Beach").id;
  const rainy = planner.buildItinerary(cox, { days: 3, tier: 'value', rainyDays: [1] });
  assert.ok(rainy.items.filter((i) => i.day === 1).every((i) => i.indoor));
  const cheap = planner.buildItinerary(cox, { days: 2, tier: 'budget' }).activityTotalBdt;
  const fancy = planner.buildItinerary(cox, { days: 2, tier: 'fancy' }).activityTotalBdt;
  assert.ok(cheap <= fancy);
});
