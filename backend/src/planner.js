'use strict';
const { BASE_DESTINATIONS, ACTIVITIES } = require('./data');

/**
 * Tier model. Weights always sum to 1 and mirror the Kotlin / Swift ScoringEngine.
 *  budget: cheapest sensible trip.  value: affordable but good quality (the proposal's default weights).
 *  fancy: comfort and experience first, cost matters little.
 */
const TIERS = {
  budget: { costFactor: 0.7, qualityStrength: 0.05, weights: { budget: 0.34, interest: 0.20, time: 0.12, weather: 0.14, route: 0.12, preference: 0.08 } },
  value:  { costFactor: 1.0, qualityStrength: 0.20, weights: { budget: 0.22, interest: 0.20, time: 0.18, weather: 0.18, route: 0.12, preference: 0.10 } },
  fancy:  { costFactor: 1.8, qualityStrength: 0.30, weights: { budget: 0.06, interest: 0.28, time: 0.16, weather: 0.22, route: 0.08, preference: 0.20 } },
};
const MAX_DISTANCE_KM = 600;
const QUALITY_PRIOR = 0.7; // neutral rating of 3.5 / 5

const clamp01 = (x) => Math.min(1, Math.max(0, x));
const round3 = (x) => Math.round(x * 1000) / 1000;

function normalizeTier(t) { return TIERS[t] ? t : 'value'; }

/** Bayesian average of 1-5 ratings, scaled to 0-1, so one rating cannot swing a destination. */
function qualityFromRatings(ratings) {
  const m = 5, prior = 3.5;
  const sum = ratings.reduce((a, b) => a + b, 0);
  return ((sum + prior * m) / (ratings.length + m)) / 5;
}

function scoreDestination(d, q, opts = {}) {
  const tier = TIERS[normalizeTier(q.tier)];
  const tripCost = d.costPerDayBdt * tier.costFactor * q.days * q.travellers;
  const budget = tripCost <= 0 ? 1 : clamp01(q.budget / tripCost);
  const interests = q.interests || [];
  const interest = interests.length === 0 ? 0.5 : d.tags.filter((t) => interests.includes(t)).length / interests.length;
  const time = 1 - Math.min(1, Math.abs(q.days - d.recommendedDays) / Math.max(q.days, d.recommendedDays));
  const weather = d.bestMonths.includes(q.month) ? 1 : 0.4;
  const route = 1 - Math.min(1, d.distanceKm / MAX_DISTANCE_KM);
  const preference = opts.favorite ? 1 : 0.5;
  const w = tier.weights;
  const base = w.budget * budget + w.interest * interest + w.time * time + w.weather * weather + w.route * route + w.preference * preference;
  const quality = opts.quality ?? QUALITY_PRIOR;
  const total = clamp01(base * (1 + tier.qualityStrength * (quality - QUALITY_PRIOR)));
  return {
    total: round3(total),
    factors: { budget: round3(budget), interest: round3(interest), time: round3(time), weather: round3(weather), route: round3(route), preference: round3(preference), quality: round3(quality) },
    estimatedCostBdt: Math.round(tripCost),
    affordable: budget >= 0.8,
  };
}

function rank(destinations, q, ctx = {}) {
  const tier = normalizeTier(q.tier);
  const rows = destinations
    .filter((d) => !(ctx.excludeIds || []).includes(d.id))
    .map((d) => ({ destination: d, score: scoreDestination(d, { ...q, tier }, { favorite: (ctx.favorites || []).includes(d.id), quality: ctx.quality ? ctx.quality[d.id] : undefined }) }));
  rows.sort((a, b) => {
    if (tier !== 'fancy' && a.score.affordable !== b.score.affordable) return a.score.affordable ? -1 : 1; // affordable first
    return b.score.total - a.score.total || a.destination.id - b.destination.id;
  });
  return rows;
}

const SLOTS = [9, 13, 16];

/** budget: cheapest first. fancy: priciest first. value: most hours of experience per taka. */
function activityOrder(tier, interests) {
  const matches = (a) => (interests.includes(a.category) ? 1 : 0);
  const key = {
    budget: (a) => a.estimatedCost,
    fancy: (a) => -a.estimatedCost,
    value: (a) => -(a.durationHours / (a.estimatedCost + 100)),
  }[tier];
  return (a, b) => matches(b) - matches(a) || key(a) - key(b) || a.id - b.id;
}

function buildItinerary(destinationId, { days = 3, interests = [], tier = 'value', rainyDays = [], travellers = 2 } = {}, activities = ACTIVITIES) {
  tier = normalizeTier(tier);
  const acts = activities.filter((a) => a.destinationId === destinationId).sort(activityOrder(tier, interests));
  const used = new Set(), items = [];
  for (let day = 1; day <= days; day++) {
    for (const hour of SLOTS) {
      const pick = acts.find((a) => !used.has(a.id) && (!rainyDays.includes(day) || a.isIndoor));
      if (!pick) continue;
      used.add(pick.id);
      items.push({ day, hour, activityId: pick.id, name: pick.name, category: pick.category, indoor: pick.isIndoor, costPerPersonBdt: pick.estimatedCost });
    }
  }
  const activityTotal = items.reduce((s, i) => s + i.costPerPersonBdt, 0) * travellers;
  return { destinationId, tier, days, items, activityTotalBdt: activityTotal };
}

/** Rough split of the per-day estimate. Shares are assumptions, not market data. */
function estimateTrip(d, { days, travellers, tier }) {
  const t = TIERS[normalizeTier(tier)];
  const total = Math.round(d.costPerDayBdt * t.costFactor * days * travellers);
  const share = { stay: 0.4, food: 0.25, transport: 0.2, activities: 0.15 };
  const breakdown = Object.fromEntries(Object.entries(share).map(([k, v]) => [k, Math.round(total * v)]));
  return { totalBdt: total, breakdown, note: 'Rough estimate, not a quote.' };
}

module.exports = { TIERS, normalizeTier, qualityFromRatings, scoreDestination, rank, buildItinerary, estimateTrip, MAX_DISTANCE_KM };
