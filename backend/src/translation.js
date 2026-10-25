'use strict';
/**
 * Optional language layer. An on-device language model (LiteRT-LM on Android, Apple's Foundation Models on iOS)
 * only TRANSLATES a message ORBIT Brain did not understand (Bangla, Banglish, loose wording) into one short English
 * request. ORBIT Brain then does all the planning, so prices, places and numbers always come from the app's own data,
 * never from the model. The model's output is sanitised and re-checked by the Brain before it is used.
 * The Kotlin and Swift versions mirror this file.
 */
const brain = require('./brain');

const INSTRUCTIONS = [
  'You convert a traveller\'s message into ONE short English request for a Bangladesh trip planner.',
  'The message may be in English, Bangla or Banglish (Bangla written in English letters).',
  'Output exactly one line and nothing else. Keep every number, amount, place name and month. Write digits as 0-9.',
  'Use these forms:',
  '- plan: "<N> days <interest> trip under <amount> for <N> people in <month>"; add cheap, affordable but good quality, or luxury when the traveller asks for a style',
  '- "itinerary for <place>"',
  '- "best time to visit <place>"',
  '- "how much for <N> people <N> days in <place>"',
  '- "compare <place> and <place>"',
  '- "something I have not visited"',
  '- "what have i visited"',
  '- "how far is <place>"',
  '- "remember that <short note>"',
  '- "forget everything"',
  'Interests are: beach, hills, nature, food, culture, adventure.',
  'If the message is not about travel, output NONE.',
  'Examples:',
  'Message: আমি ৩ দিনের জন্য সমুদ্র সৈকতে যেতে চাই, বাজেট ২০ হাজার',
  'Output: 3 days beach trip under 20k',
  'Message: cox bazar jete koto khoroch hobe 4 jon',
  'Output: how much for 4 people in Cox\'s Bazar',
  'Message: শীতে পাহাড়ে ঘুরতে চাই, সস্তায়',
  'Output: cheap hills trip in winter',
  'Message: what is 2+2',
  'Output: NONE',
].join('\n');

const MAX_WORDS = 25;
const NOTICE = 'Read with the on-device language model.';

/** Cleans raw model output into a single safe command, or returns null if it is unusable. */
function sanitizeCommand(raw) {
  if (typeof raw !== 'string') return null;
  const line = raw.split(/\r?\n/).map((l) => l.trim()).find((l) => l.length > 0);
  if (!line) return null;
  let s = line.replace(/^(?:output|command|answer)\s*:\s*/i, '')
    .replace(/^[\s"'`*_>\-]+|[\s"'`*_]+$/g, '')
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/\?/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (!s || /^none\.?$/i.test(s)) return null;
  if (!/[a-z]/i.test(s)) return null;
  if (s.split(' ').length > MAX_WORDS || s.length > 160) return null;
  return s;
}

/**
 * Brain first. Only if it did not understand, ask the translator and try again.
 * `translate(text)` may return null or throw: the original "did not understand" reply is kept in that case.
 */
async function respondAssisted(utterance, ctx, state, translate) {
  const first = brain.respond(utterance, ctx, state);
  if (first.understood || typeof translate !== 'function') return { ...first, translatedFrom: null };
  let command = null;
  try { command = sanitizeCommand(await translate(utterance)); } catch { command = null; }
  if (!command) return { ...first, translatedFrom: null };
  const second = brain.respond(command, ctx, state);
  if (!second.understood) return { ...first, translatedFrom: null };
  // The model's own words are never shown. Only a fixed notice plus the Brain's answer reach the traveller.
  return { ...second, reply: `${NOTICE}\n${second.reply}`, translatedFrom: command };
}

module.exports = { INSTRUCTIONS, NOTICE, sanitizeCommand, respondAssisted };
