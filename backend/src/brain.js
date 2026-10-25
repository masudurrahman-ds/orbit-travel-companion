'use strict';
/**
 * ORBIT Brain: the app's own travel agent. No external AI service, no network.
 * Pipeline: normalise -> extract entities -> detect intent -> apply defaults and memory -> call planner tools -> write a short reply.
 * The Kotlin and Swift versions follow this file step by step and share data/brain-data.json.
 */
const planner = require('./planner');
const LEX = require('../data/brain-data.json');
const transport = require('./transport');

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const TIER_LABEL = { budget: 'Budget', value: 'Value', fancy: 'Fancy' };

// ---------------------------------------------------------------- text helpers
// Bangla script and Banglish are understood offline with the word lists in brain-data.json: no model needed.
const BN_DIGITS = '০১২৩৪৫৬৭৮৯';
const BN_WORDS = Object.entries(LEX.bangla || {}).sort((a, b) => b[0].length - a[0].length);
function prepareBangla(text) {
  let s = String(text).replace(/[০-৯]/g, (d) => String(BN_DIGITS.indexOf(d)));
  if (!/[\u0980-\u09FF]/.test(s)) return s;
  s = s.replace(/(\d+(?:\.\d+)?)\s*হাজার/g, '$1k ').replace(/(\d+(?:\.\d+)?)\s*(?:লাখ|লক্ষ)/g, '$1 lakh ')
    .replace(/(\d+)\s*জন/g, '$1 people ').replace(/(\d+)\s*দিন/g, '$1 days ').replace(/(\d+)\s*রাত/g, '$1 nights ').replace(/(\d+)\s*সপ্তাহ/g, '$1 weeks ');
  for (const [bn, en] of BN_WORDS) s = s.split(bn).join(' ' + en + ' ');
  return s;
}

function normalize(text) {
  let s = prepareBangla(text || '').toLowerCase().replace(/[\u2019']/g, '');
  s = s.replace(/৳/g, ' bdt ').replace(/\b(taka|tk)\b/g, ' bdt ');
  s = s.replace(/(\d),(\d{3})/g, '$1$2');
  s = s.replace(/[^a-z0-9. ]+/g, ' ').replace(/(?<!\d)\.|\.(?!\d)/g, ' ');
  s = s.split(' ').filter(Boolean).map((t) => (LEX.numberWords[t] !== undefined ? String(LEX.numberWords[t]) : (LEX.banglish[t] !== undefined ? LEX.banglish[t] : t))).join(' ');
  s = ' ' + s.replace(/\s+/g, ' ').trim() + ' ';
  for (const [from, to] of Object.entries(LEX.placeAliases)) s = s.split(' ' + from + ' ').join(' ' + to + ' ');
  return s;
}
const has = (norm, phrase) => norm.includes(' ' + phrase + ' ');
const hasAny = (norm, list) => list.some((p) => has(norm, p));
const bdt = (n) => 'BDT ' + Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
const clampInt = (v, lo, hi) => Math.min(hi, Math.max(lo, Math.round(v)));

// ---------------------------------------------------------------- entity extraction
function extractBudget(norm) {
  let m = norm.match(/ (\d+(?:\.\d+)?) ?(k|thousand|lakh|lac) /);
  if (m) { const mult = m[2] === 'k' || m[2] === 'thousand' ? 1000 : 100000; return clampInt(parseFloat(m[1]) * mult, 1000, 10000000); }
  m = norm.match(/ bdt (\d{4,7}) /) || norm.match(/ (\d{4,7}) bdt /);
  if (m) return clampInt(Number(m[1]), 1000, 10000000);
  m = norm.match(/ (?:under|below|within|budget of|budget|max|upto|up to|around|about) (\d{4,7}) /);
  if (m) return clampInt(Number(m[1]), 1000, 10000000);
  return null;
}

function extractDays(norm) {
  let m = norm.match(/ (\d+) (?:day|days) /);
  if (m) return clampInt(Number(m[1]), 1, 14);
  m = norm.match(/ (\d+) (?:night|nights) /);
  if (m) return clampInt(Number(m[1]) + 1, 1, 14);
  m = norm.match(/ (\d+) (?:week|weeks) /);
  if (m) return clampInt(Number(m[1]) * 7, 1, 14);
  if (has(norm, 'long weekend')) return 3;
  if (has(norm, 'weekend')) return 2;
  if (has(norm, 'day trip') || has(norm, 'a day') || has(norm, 'one day')) return 1;
  if (has(norm, 'a week') || has(norm, 'week long') || has(norm, 'weeklong')) return 7;
  return null;
}

function extractTravellers(norm) {
  let m = norm.match(/ family of (\d+) /) || norm.match(/ (\d+) (?:people|persons|person|travellers|travelers|adults|pax) /) || norm.match(/ (\d+) of us /);
  if (m) return clampInt(Number(m[1]), 1, 30);
  m = norm.match(/ for (\d{1,2}) (?!(?:day|days|night|nights|week|weeks|k|thousand|lakh|lac|bdt)\b)/);
  if (m) return clampInt(Number(m[1]), 1, 30);
  m = norm.match(/ with (\d+) (?:friends|friend|colleagues|cousins) /);
  if (m) return clampInt(Number(m[1]) + 1, 1, 30);
  if (hasAny(norm, ['solo', 'alone', 'myself', 'by myself', 'just me'])) return 1;
  if (hasAny(norm, ['couple', 'honeymoon', 'wife', 'husband', 'partner', 'girlfriend', 'boyfriend', 'two of us'])) return 2;
  if (has(norm, 'family')) return 4;
  if (hasAny(norm, ['friends', 'group'])) return 4;
  return null;
}

function extractMonth(norm, currentMonth) {
  if (has(norm, 'this month')) return currentMonth;
  if (has(norm, 'next month')) return (currentMonth % 12) + 1;
  for (const [name, n] of Object.entries(LEX.monthNames)) if (has(norm, name)) return n;
  if (norm.includes(' in may ') || norm.includes(' this may ') || norm.includes(' next may ')) return 5;
  if (has(norm, 'march')) return 3;
  for (const [name, n] of Object.entries(LEX.seasons)) if (has(norm, name)) return n;
  return null;
}

function extractInterests(norm) {
  return Object.entries(LEX.interests).filter(([, words]) => hasAny(norm, words)).map(([tag]) => tag);
}

function extractTier(norm) {
  const cheap = hasAny(norm, LEX.cheapWords), quality = hasAny(norm, LEX.qualityWords), fancy = hasAny(norm, LEX.fancyWords);
  if (fancy) return 'fancy';
  if (cheap && quality) return 'value';
  if (quality) return 'value';
  if (cheap) return 'budget';
  return null;
}

const wordsOf = (name) => normalize(name).trim();
function destinationKeys(d) {
  const base = wordsOf(d.name.split(/[(,]/)[0]);
  const keys = new Set([wordsOf(d.name), base, wordsOf(d.district)]);
  const parts = base.split(' ');
  if (parts.length >= 3) {
    const two = parts.slice(0, 2).join(' ');
    keys.add(two);
    if (two.endsWith('s')) keys.add(two.slice(0, -1)); // "saint martins" -> "saint martin"
  }
  // A distinctive first word on its own ("sajek", "patenga", "nilgiri") is enough to name a spot.
  if (parts.length >= 2 && parts[0].length >= 5 && !LEX.firstWordStoplist.includes(parts[0])) keys.add(parts[0]);
  return [...keys].filter((k) => k.length >= 4);
}

/** Finds destinations named in the text. Returns [{dest, key}] best first. */
function findDestinations(norm, destinations) {
  const cleaned = norm.replace(/ from [a-z0-9]+ /g, ' ');
  const found = [];
  for (const d of destinations) {
    let best = null;
    for (const k of destinationKeys(d)) if (has(cleaned, k) && (!best || k.length > best.length)) best = k;
    if (best) found.push({ dest: d, key: best, pos: cleaned.indexOf(' ' + best + ' '), byDistrict: best === wordsOf(d.district) && best !== wordsOf(d.name.split(/[(,]/)[0]) });
  }
  found.sort((a, b) => b.key.length - a.key.length || a.dest.id - b.dest.id);
  return found;
}

// ---------------------------------------------------------------- memory and defaults
function readPreferences(memory) {
  const likes = new Set(), dislikes = new Set();
  let tier = null;
  for (const note of memory) {
    const n = normalize(note.text);
    const negative = hasAny(n, ['hate', 'dislike', 'avoid', 'not into', 'no']);
    for (const t of extractInterests(n)) (negative ? dislikes : likes).add(t);
    if (!negative) tier = extractTier(n) || tier;
  }
  return { likes: [...likes], dislikes: [...dislikes], tier };
}

function historyInterests(visits, destinations) {
  const counts = {};
  const byId = new Map(destinations.map((d) => [d.id, d]));
  for (const v of visits) for (const t of (byId.get(v.destinationId)?.tags || [])) counts[t] = (counts[t] || 0) + 1;
  return Object.entries(counts).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).slice(0, 2).map(([t]) => t);
}

function resolveQuery(ent, ctx, state, prefs) {
  const assumed = [];
  const last = state.lastQuery || null;
  const tier = ent.tier || (last && last.tier) || prefs.tier || 'value';
  if (!ent.tier && !(last && last.tier) && !prefs.tier) assumed.push(`${TIER_LABEL[tier]} tour`);
  const travellers = ent.travellers ?? (last && last.travellers) ?? 2;
  if (ent.travellers == null && !last) assumed.push(`${travellers} travellers`);
  const days = ent.days ?? (last && last.days) ?? 3;
  if (ent.days == null && !last) assumed.push(`${days} days`);
  const month = ent.month ?? (last && last.month) ?? ctx.month;
  if (ent.month == null && !last) assumed.push(MONTHS[month - 1]);
  let interests = ent.interests.length ? ent.interests : (last && last.interests) || [];
  if (!ent.interests.length && !last) {
    const learned = prefs.likes.length ? prefs.likes : historyInterests(ctx.visits, ctx.destinations);
    interests = learned;
    if (learned.length) assumed.push(`interests: ${learned.join(', ')}`);
  }
  interests = interests.filter((t) => !prefs.dislikes.includes(t));
  let budget = ent.budget ?? (last && !ent.tier && !ent.days && !ent.travellers ? last.budget : null);
  if (budget == null) {
    budget = Math.round((LEX.dailyPerPerson[tier] * days * travellers) / 500) * 500;
    assumed.push(`budget about ${bdt(budget)}`);
  }
  return { query: { budget, days, travellers, month, interests, tier }, assumed };
}

// ---------------------------------------------------------------- reply building
function whyLine(row, query) {
  const d = row.destination, f = row.score.factors, bits = [];
  bits.push(f.weather === 1 ? `in season in ${MONTHS[query.month - 1]}` : `off-season in ${MONTHS[query.month - 1]}, so expect rain`);
  if (query.interests.length && f.interest >= 0.99) bits.push(`matches ${query.interests.join(' and ')}`);
  else if (query.interests.length && f.interest > 0) bits.push(`partly matches ${query.interests.join(', ')}`);
  bits.push(row.score.affordable ? 'within your budget' : `about ${bdt(row.score.estimatedCostBdt - query.budget)} over budget`);
  return bits.join(', ');
}

function safetyNote(dest) {
  return dest.tags.some((t) => LEX.safetyTags.includes(t)) ? ' Check weather and local advisories before you go.' : '';
}

/** The most severe active advisory that applies to this spot (national, its division or its district). */
function advisoryNote(dest, ctx) {
  const rank = { danger: 3, warning: 2, info: 1 };
  const list = (ctx.advisories || []).filter((a) => a.scope === 'national' || (a.scope === 'division' && a.target === dest.division) || (a.scope === 'district' && a.target === dest.district));
  if (!list.length) return '';
  list.sort((a, b) => (rank[b.severity] || 0) - (rank[a.severity] || 0));
  const a = list[0];
  return ` Advisory (${a.severity}): ${a.title}.${a.text ? ' ' + a.text : ''}`;
}

function planFor(dest, query, ctx) {
  const it = planner.buildItinerary(dest.id, { days: query.days, interests: query.interests, tier: query.tier, travellers: query.travellers }, ctx.activities);
  return { destination: { id: dest.id, name: dest.name, district: dest.district }, ...it, estimate: planner.estimateTrip(dest, query) };
}

function itineraryText(plan) {
  const lines = [];
  for (let day = 1; day <= plan.days; day++) {
    const items = plan.items.filter((i) => i.day === day);
    if (items.length) lines.push(`Day ${day}: ` + items.map((i) => `${String(i.hour).padStart(2, '0')}:00 ${i.name}`).join('; '));
  }
  return lines.join('\n');
}

const SUGGESTIONS = "Try: \"3 days beach trip under 20k\", \"cheap hills trip for 4 people\", \"affordable but good quality trip in December\", \"fancy weekend in Sylhet\", \"itinerary for Jaflong\", \"best time to visit Sajek\", \"compare Sylhet and Bandarban\", \"something I have not visited\".";

// ---------------------------------------------------------------- the agent
function respond(utterance, ctx, state = {}) {
  const norm = normalize(utterance);
  const month = ctx.month;
  const prefs = readPreferences(ctx.memory || []);
  const ent = {
    budget: extractBudget(norm), days: extractDays(norm), travellers: extractTravellers(norm), month: extractMonth(norm, month),
    interests: extractInterests(norm), tier: extractTier(norm),
  };
  const dests = findDestinations(norm, ctx.destinations);
  const visitedIds = new Set(ctx.visits.map((v) => v.destinationId));
  const t = LEX.triggers;
  const out = (reply, extra = {}) => ({ reply, state: { ...state, ...(extra.state || {}) }, plan: extra.plan || null, actions: extra.actions || [], understood: extra.understood !== false });
  const anyEntity = ent.budget != null || ent.days != null || ent.travellers != null || ent.month != null || ent.interests.length > 0 || ent.tier != null;
  const shortText = norm.trim().split(' ').length <= 4;

  // --- housekeeping intents
  if (hasAny(norm, t.forget)) return out('Done. I cleared your saved preferences.', { actions: [{ type: 'forget_all' }], state: { lastQuery: null, lastPlace: null, offset: 0 } });
  if (hasAny(norm, t.remember)) {
    const m = String(utterance).match(/(?:remember(?: that)?|note that|keep in mind(?: that)?)\s+(.+)/i) || [null, String(utterance)];
    const note = m[1].replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, 200);
    return out(`Saved: "${note}". I will use it in future plans.`, { actions: [{ type: 'remember', text: note }] });
  }
  if (hasAny(norm, t.save) && state.lastPlan) return out(`Saved the ${state.lastPlan.destination.name} plan as your trip.`, { actions: [{ type: 'save_plan' }] });
  if (shortText && hasAny(norm, t.thanks)) return out('You are welcome. Tell me when you want the next plan.');
  if (shortText && hasAny(norm, t.greeting) && !anyEntity) return out('Hello. Tell me what kind of trip you want, or say "something I have not visited". I will fill in the details.');
  if (hasAny(norm, t.help) && !hasAny(norm, t.plan)) return out('I can plan trips by budget, days, interests and season, pick Budget, Value or Fancy tours, build itineraries, estimate costs, compare places, suggest places you have not visited, and remember your preferences. ' + SUGGESTIONS);

  // Explain a recommendation with evidence from the destination dataset and current assumptions.
  if (hasAny(norm, t.why)) {
    const match = dests[0] || (state.lastPlace && ctx.destinations.find((d) => d.id === state.lastPlace));
    if (!match) return out('I can explain the trade-offs, but I need a destination to evaluate first. Ask me to recommend a trip, then ask "why this place?".');
    const { query, assumed } = resolveQuery(ent, ctx, state, prefs);
    const d = dests.length ? pickFor(dests[0], query, ctx) : match;
    const estimate = planner.estimateTrip(d, query);
    const cost = estimate.totalBdt;
    const season = d.bestMonths.includes(query.month) ? 'your selected month is in its listed best season' : 'your selected month is outside its listed best season';
    const tags = Array.isArray(d.tags) ? d.tags : String(d.tags || '').split(',');
    const matched = tags.filter((tag) => query.interests.includes(tag));
    const fit = matched.length ? 'It matches your interests in ' + matched.join(', ') + '.' : 'Its recorded travel themes are ' + tags.join(', ') + '.';
    const budgetFit = cost <= query.budget ? 'The estimated total, ' + bdt(cost) + ', is within your ' + bdt(query.budget) + ' budget.' : 'The estimated total, ' + bdt(cost) + ', is above your ' + bdt(query.budget) + ' budget, so switch to Budget style or shorten the trip.';
    return out('Why ' + d.name + ': ' + fit + ' The season check says ' + season + '. ' + budgetFit + ' These are planning estimates, not live quotes.' + (assumed.length ? ' Assumptions: ' + assumed.join(', ') + '.' : ''), { state: { lastQuery: query, lastPlace: d.id } });
  }

  // --- progress
  if (hasAny(norm, t.progress)) {
    const districts = new Set(ctx.destinations.filter((d) => visitedIds.has(d.id)).map((d) => d.district));
    const total = new Set(ctx.destinations.map((d) => d.district)).size;
    if (visitedIds.size === 0) return out(`You have not marked any spots yet. Open the Map tab and tap a district to mark places you have visited.`);
    const recent = ctx.visits.slice(-3).map((v) => ctx.destinations.find((d) => d.id === v.destinationId)?.name).filter(Boolean);
    return out(`You have visited ${visitedIds.size} of ${ctx.destinations.length} spots across ${districts.size} of ${total} districts. Latest: ${recent.join(', ')}.`);
  }

  // --- navigation with live location
  if (hasAny(norm, t.nav)) {
    const loc = ctx.location;
    const target = (dests[0] && dests[0].dest) || (loc && loc.targetId && ctx.destinations.find((d) => d.id === loc.targetId)) || (state.lastPlace && ctx.destinations.find((d) => d.id === state.lastPlace));
    if (!loc) return out('Open the Live map and start tracking, then ask again. I need your location to measure the distance.');
    if (!target) return out('Plan a trip first, or name the place, and I will measure the distance from where you are.');
    const km = haversine(loc.lat, loc.lon, target.latitude, target.longitude);
    const roadKm = km * 1.3, hours = roadKm / 40;
    const time = hours < 1 ? `${Math.max(5, Math.round(hours * 60))} minutes` : `${hours.toFixed(1)} hours`;
    const near = km <= 10 ? ` You are inside ${target.district}. You can mark ${target.name} as visited.` : '';
    return out(`${target.name}: about ${km.toFixed(0)} km straight-line, roughly ${roadKm.toFixed(0)} km by road, about ${time} at 40 km/h. Spot positions are district-level, so this is rough.${near}`, { state: { lastPlace: target.id } });
  }

  // --- compare
  // One mention per named place or district, in the order the traveller wrote them.
  const mentionMap = new Map();
  for (const x of dests) { const k = x.byDistrict ? 'd:' + x.dest.district : 'n:' + x.dest.id; if (!mentionMap.has(k)) mentionMap.set(k, x); }
  const mentions = [...mentionMap.values()].sort((a, b) => a.pos - b.pos || a.dest.id - b.dest.id);
  if (mentions.length >= 2 && (hasAny(norm, t.compare) || has(norm, 'and'))) {
    const { query } = resolveQuery(ent, ctx, state, prefs);
    const a = pickFor(mentions[0], query, ctx), b = pickFor(mentions[1], query, ctx);
    const ra = rowFor(a, query, ctx), rb = rowFor(b, query, ctx);
    const winner = ra.score.total >= rb.score.total ? ra : rb, other = winner === ra ? rb : ra;
    const line = (r) => `${r.destination.name}: ${bdt(r.score.estimatedCostBdt)}, ${r.destination.bestMonths.includes(query.month) ? 'in season' : 'off-season'} in ${MONTHS[query.month - 1]}, ${r.destination.tags.join('/')}`;
    return out(`${winner.destination.name} fits better for ${query.days} days, ${query.travellers} travellers, ${TIER_LABEL[query.tier]} tour.\n${line(winner)}\n${line(other)}\nCosts are estimates.`, { state: { lastQuery: query, lastPlace: winner.destination.id } });
  }

  // --- transport names and dated fare evidence (never present a cached fare as live)
  const asksTransport = /\\b(bus|coach|train|rail|flight|plane|airfare|transport|fare|ticket|route|how to get)\\b/.test(norm);
  if (asksTransport && (dests.length || state.lastPlace)) {
    const match = dests[0] || { dest: ctx.destinations.find((x) => x.id === state.lastPlace), byDistrict: false };
    const d = match.dest ? pickFor(match, resolveQuery(ent, ctx, state, prefs).query, ctx) : null;
    if (d) {
      const info = transport.estimateTransport(d);
      const route = transport.COSTS.entries.find((e) => e.category === 'transport' && e.district === d.district && e.id.startsWith('route_'));
      const v = route?.value || {};
      const parts = [];
      if (v.bus_nonac_tk) parts.push('Non-AC bus: ' + bdt(v.bus_nonac_tk.seats51) + ' (51-seat chart) or ' + bdt(v.bus_nonac_tk.seats40) + ' (40-seat chart), one way from Dhaka.');
      else if (d.distanceKm > 10) parts.push('Non-AC bus estimate: ' + bdt(Math.round(d.distanceKm * transport.PER_KM.low / 10) * 10) + '–' + bdt(Math.round(d.distanceKm * transport.PER_KM.high / 10) * 10) + ' one way, based on a per-km rate and approximate road distance.');
      if (v.train_tk) parts.push('Train classes/fare entries: ' + Object.entries(v.train_tk).map(([k, n]) => k.replaceAll('_', ' ') + ' ' + bdt(n)).join('; ') + '.');
      if (v.air_oneway_from_tk) parts.push('Flight listing from ' + bdt(v.air_oneway_from_tk) + ' one way; dynamic fare, not a quote.');
      if (!parts.length) parts.push('I do not have a verified route-specific fare for this destination in the local dataset yet.');
      const checked = route?.asOf || transport.COSTS.entries.find((e) => e.id === 'bus_nonac_perkm_hike')?.asOf || transport.COSTS.meta.compiledOn;
      return out('Getting to ' + d.name + ' from Dhaka:\\n' + parts.join('\\n') + '\\nFare data checked: ' + checked + '. This is dated reference data, not a live operator quote. Fares, availability and schedules can change; confirm with the railway/operator before paying. I cannot confirm real-time seat availability from this dataset.', { state: { lastPlace: d.id } });
    }
  }

  // --- when to go
  if (hasAny(norm, t.when) && (dests.length || state.lastPlace)) {
    const d = dests.length ? pickFor(dests[0], resolveQuery(ent, ctx, state, prefs).query, ctx) : ctx.destinations.find((x) => x.id === state.lastPlace);
    const ms = d.bestMonths.slice().sort((x, y) => ((x + 8) % 12) - ((y + 8) % 12)).map((m) => MONTHS[m - 1]);
    const now = d.bestMonths.includes(month) ? `${MONTHS[month - 1]} is in season.` : `${MONTHS[month - 1]} is off-season, so expect rain.`;
    return out(`Best months for ${d.name}: ${ms.join(', ')}. ${now}${safetyNote(d)}${advisoryNote(d, ctx)}`, { state: { lastPlace: d.id } });
  }

  // --- cost
  if (hasAny(norm, t.cost) && (dests.length || state.lastPlace)) {
    const { query } = resolveQuery(ent, ctx, state, prefs);
    const d = dests.length ? pickFor(dests[0], query, ctx) : ctx.destinations.find((x) => x.id === state.lastPlace);
    const est = planner.estimateTrip(d, query), b = est.breakdown;
    const fits = est.totalBdt <= query.budget ? 'within' : 'over';
    return out(`${d.name}, ${query.days} days, ${query.travellers} travellers, ${TIER_LABEL[query.tier]} tour: about ${bdt(est.totalBdt)}. Stay ${bdt(b.stay)}, food ${bdt(b.food)}, transport ${bdt(b.transport)}, activities ${bdt(b.activities)}. That is ${fits} a ${bdt(query.budget)} budget. Estimates only.`, { state: { lastQuery: query, lastPlace: d.id } });
  }

  // --- itinerary for a named place (or the last one)
  if (hasAny(norm, t.itinerary) && (dests.length || state.lastPlace)) {
    const { query, assumed } = resolveQuery(ent, ctx, state, prefs);
    const d = dests.length ? pickFor(dests[0], query, ctx) : ctx.destinations.find((x) => x.id === state.lastPlace);
    const plan = planFor(d, query, ctx);
    const text = itineraryText(plan);
    const thin = plan.items.length < query.days * 3 ? '\nOnly ' + plan.items.length + ' activities are listed for this spot so far.' : '';
    return out(`${d.name}, ${query.days} days, ${TIER_LABEL[query.tier]} tour, about ${bdt(plan.estimate.totalBdt)} for ${query.travellers}.\n${text || 'No suitable activities found.'}${thin}${assumed.length ? `\nAssumed: ${assumed.join(', ')}.` : ''}${safetyNote(d)}${advisoryNote(d, ctx)}`, { plan, state: { lastQuery: query, lastPlace: d.id, lastPlan: plan, offset: 0 } });
  }

  // --- info about a place
  if (dests.length && (hasAny(norm, t.info) || (!anyEntity && !hasAny(norm, t.plan)))) {
    const { query } = resolveQuery(ent, ctx, state, prefs);
    const d = pickFor(dests[0], query, ctx);
    const est = planner.estimateTrip(d, query);
    const months = d.bestMonths.map((m) => MONTHS[m - 1].slice(0, 3)).join(', ');
    return out(`${d.name} (${d.district}): ${d.blurb} Tags: ${d.tags.join(', ')}. Best months: ${months}. About ${bdt(est.totalBdt)} for ${query.days} days, ${query.travellers} travellers, ${TIER_LABEL[query.tier]} tour (estimate).${safetyNote(d)}${advisoryNote(d, ctx)}`, { state: { lastPlace: d.id } });
  }

  // --- follow-ups on the previous plan: "another one", "cheaper", "what about fancy", "make it 5 days"
  const tokens = norm.trim().split(' ').length;
  const modifier = !dests.length && anyEntity && !hasAny(norm, t.plan) && tokens <= 8;
  const followUp = !!state.lastQuery && (hasAny(norm, t.alt) || hasAny(norm, t.cheaper) || hasAny(norm, t.fancier) || modifier);
  if (followUp) {
    if (hasAny(norm, t.cheaper)) ent.tier = 'budget';
    if (hasAny(norm, t.fancier)) ent.tier = 'fancy';
  }

  // --- planning (the default for anything travel-shaped)
  const wantsNew = hasAny(norm, t.nextNew);
  if (hasAny(norm, t.plan) || anyEntity || wantsNew || followUp || dests.length > 0) {
    let q, assumed = [];
    if (followUp) {
      q = { ...state.lastQuery, ...definedOnly({ tier: ent.tier, budget: ent.budget, days: ent.days, travellers: ent.travellers, month: ent.month }) };
      if (ent.interests.length) q.interests = ent.interests;
      if (ent.budget == null && (ent.tier || ent.days || ent.travellers)) q.budget = Math.round((LEX.dailyPerPerson[q.tier] * q.days * q.travellers) / 500) * 500;
    } else {
      ({ query: q, assumed } = resolveQuery(ent, ctx, { ...state, lastQuery: null }, prefs));
    }
    let pool = ctx.destinations;
    if (dests.length) {
      // Naming a specific spot plans that spot. Naming only a district considers every spot in it.
      const specific = dests.filter((x) => !x.byDistrict).map((x) => x.dest.id);
      if (specific.length) pool = pool.filter((d) => specific.includes(d.id));
      else { const districts = new Set(dests.map((x) => x.dest.district)); pool = pool.filter((d) => districts.has(d.district)); }
    }
    const rows = planner.rank(pool, q, { excludeIds: wantsNew ? [...visitedIds] : [], quality: ctx.quality });
    if (!rows.length) return out(wantsNew ? 'You have visited every spot in the list. Add new spots from More, Add a spot.' : 'I found nothing for that. ' + SUGGESTIONS);
    const offset = hasAny(norm, t.alt) ? ((state.offset || 0) + 1) % Math.min(rows.length, 5) : 0;
    const top = rows[offset];
    const others = rows.filter((_, i) => i !== offset).slice(0, 2);
    const plan = planFor(top.destination, q, ctx);
    const seen = visitedIds.has(top.destination.id) ? ' You have visited it before.' : '';
    const alsoLine = others.length ? `\nAlso good: ${others.map((r) => `${r.destination.name} (${bdt(r.score.estimatedCostBdt)})`).join(', ')}.` : '';
    const head = `${TIER_LABEL[q.tier]} tour pick: ${top.destination.name} (${top.destination.district}), about ${bdt(top.score.estimatedCostBdt)} for ${q.travellers} ${q.travellers === 1 ? 'person' : 'people'}, ${q.days} ${q.days === 1 ? 'day' : 'days'}. ${capitalize(whyLine(top, q))}.${seen}`;
    const assumedLine = assumed.length ? `\nAssumed: ${assumed.join(', ')}.` : '';
    return out(`${head}${alsoLine}${assumedLine}\nCosts are estimates.${safetyNote(top.destination)}${advisoryNote(top.destination, ctx)}`, { plan, state: { lastQuery: q, lastPlace: top.destination.id, lastPlan: plan, offset } });
  }

  // --- nothing matched: stay calm, give examples, do not interrogate
  return out('I did not catch a trip request in that. ' + SUGGESTIONS, { understood: false });
}

// ---------------------------------------------------------------- small helpers
const capitalize = (s) => s.charAt(0).toUpperCase() + s.slice(1);
const definedOnly = (o) => Object.fromEntries(Object.entries(o).filter(([, v]) => v != null));
function haversine(lat1, lon1, lat2, lon2) {
  const r = 6371, rad = Math.PI / 180, dLat = (lat2 - lat1) * rad, dLon = (lon2 - lon1) * rad;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(a));
}
/** If the user named only a district, take the best-ranked spot in it for their query. */
function pickFor(match, query, ctx) {
  if (!match.byDistrict) return match.dest;
  const rows = planner.rank(ctx.destinations.filter((d) => d.district === match.dest.district), query, { quality: ctx.quality });
  return rows[0].destination;
}
function rowFor(dest, query, ctx) {
  return { destination: dest, score: planner.scoreDestination(dest, query, { quality: ctx.quality ? ctx.quality[dest.id] : undefined }) };
}

module.exports = { respond, normalize, extractBudget, extractDays, extractTravellers, extractMonth, extractInterests, extractTier, findDestinations, readPreferences };
