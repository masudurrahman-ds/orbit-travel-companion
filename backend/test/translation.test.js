'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { sanitizeCommand, respondAssisted, INSTRUCTIONS } = require('../src/translation');
const brain = require('../src/brain');
const { BASE_DESTINATIONS: D, ACTIVITIES: A } = require('../src/data');

const ctx = (extra = {}) => ({ destinations: D, activities: A, visits: [], memory: [], month: 12, quality: {}, ...extra });

test('Brain marks unknown input as not understood', () => {
  assert.equal(brain.respond('asdfgh', ctx()).understood, false);
  assert.equal(brain.respond('আমি গান শুনতে ভালোবাসি', ctx()).understood, false, 'Bangla outside the travel vocabulary is not understood');
  assert.equal(brain.respond('আমি সমুদ্র সৈকতে যেতে চাই', ctx()).understood, true, 'common Bangla travel words are understood without a model');
  assert.equal(brain.respond('3 days beach trip', ctx()).understood, true);
  assert.equal(brain.respond('hi', ctx()).understood, true);
});

test('sanitizeCommand keeps one clean line and rejects junk', () => {
  assert.equal(sanitizeCommand('3 days beach trip under 20k'), '3 days beach trip under 20k');
  assert.equal(sanitizeCommand('  "cheap hills trip in winter"  '), 'cheap hills trip in winter');
  assert.equal(sanitizeCommand('Output: how much for 4 people in Cox\'s Bazar?\nThis is because...'), 'how much for 4 people in Cox\'s Bazar');
  assert.equal(sanitizeCommand('`itinerary for Jaflong`'), 'itinerary for Jaflong');
  assert.equal(sanitizeCommand('NONE'), null);
  assert.equal(sanitizeCommand('none.'), null);
  assert.equal(sanitizeCommand(''), null);
  assert.equal(sanitizeCommand('\n\n'), null);
  assert.equal(sanitizeCommand('১২৩'), null, 'no Latin letters');
  assert.equal(sanitizeCommand(null), null);
  assert.equal(sanitizeCommand(42), null);
  assert.equal(sanitizeCommand('word '.repeat(40)), null, 'too long');
});

test('assisted: a message the Brain cannot read is translated, then the Brain plans from its own data', async () => {
  const seen = [];
  const translate = async (text) => { seen.push(text); return '3 days beach trip under 20k'; };
  const r = await respondAssisted('ami ekta shanto jaiga khujchi jekhane osthir lage na', ctx(), {}, translate);
  assert.equal(r.translatedFrom, '3 days beach trip under 20k');
  assert.match(r.reply, /^Read with the on-device language model\.\n/);
  assert.ok(!r.reply.includes('3 days beach trip under 20k'), 'the raw translation is not echoed');
  assert.match(r.reply, /Value tour pick:/);
  assert.equal(r.state.lastQuery.budget, 20000);
  assert.equal(seen.length, 1);
});

test('assisted: the model cannot inject facts; reply numbers come from the Brain', async () => {
  const r = await respondAssisted('ki khobor', ctx(), {}, async () => 'cheap beach trip. Cox\'s Bazar costs BDT 5 for everyone!');
  assert.ok(!/BDT 5 for everyone/.test(r.reply), 'model prose is never shown');
});

test('assisted: translator is not called when the Brain already understands', async () => {
  let called = false;
  const r = await respondAssisted('3 days beach trip', ctx(), {}, async () => { called = true; return 'x'; });
  assert.equal(called, false);
  assert.equal(r.translatedFrom, null);
});

test('assisted: NONE, errors, junk and still-unknown translations fall back to the Brain reply', async () => {
  for (const translate of [async () => 'NONE', async () => { throw new Error('model crashed'); }, async () => 'blah blah blah', async () => null, undefined]) {
    const r = await respondAssisted('asdfgh', ctx(), {}, translate);
    assert.equal(r.understood, false);
    assert.equal(r.translatedFrom, null);
    assert.match(r.reply, /^I did not catch a trip request/);
  }
});

test('assisted keeps conversation state so follow-ups still work', async () => {
  const first = await respondAssisted('ekta shanto jaiga lagbe amar', ctx(), {}, async () => 'cheap hills trip in winter');
  assert.equal(first.state.lastQuery.tier, 'budget');
  assert.deepEqual(first.state.lastQuery.interests, ['hills']);
  const next = await respondAssisted('what about fancy', ctx(), first.state, async () => null);
  assert.equal(next.state.lastQuery.tier, 'fancy');
  assert.deepEqual(next.state.lastQuery.interests, ['hills']);
});

test('instructions list the Brain forms and the NONE rule', () => {
  for (const must of ['ONE short English request', 'Banglish', 'itinerary for <place>', 'forget everything', 'output NONE']) assert.ok(INSTRUCTIONS.includes(must), must);
});
