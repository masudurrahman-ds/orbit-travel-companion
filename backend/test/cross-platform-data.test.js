'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const readJson = (relative) => JSON.parse(fs.readFileSync(path.join(root, relative), 'utf8'));

test('brain and researched travel-cost data stay in sync across backend, Android and iOS', () => {
  for (const name of ['brain-data.json', 'travel-costs.json']) {
    const backend = readJson(`backend/data/${name}`);
    assert.deepEqual(readJson(`android/app/src/main/assets/${name}`), backend, `Android ${name} differs from backend`);
    assert.deepEqual(readJson(`ios/ORBIT/${name}`), backend, `iOS ${name} differs from backend`);
  }
});

test('destination reference data contains 113 spots across all 64 districts', () => {
  const { destinations, districts } = readJson('backend/data/destinations.json');
  assert.equal(destinations.length, 113);
  assert.equal(districts.length, 64);
  assert.deepEqual([...new Set(destinations.map((spot) => spot.district))].sort(), [...districts].sort());
});
