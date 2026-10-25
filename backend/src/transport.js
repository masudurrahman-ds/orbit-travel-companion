'use strict';
const COSTS = require('../data/travel-costs.json');

const byId = Object.fromEntries(COSTS.entries.map((e) => [e.id, e]));
const PER_KM = { low: byId.bus_nonac_perkm.value.taka_per_km, high: byId.bus_nonac_perkm_hike.value.taka_per_km };
const round10 = (n) => Math.round(n / 10) * 10;

/**
 * Getting there from Dhaka. Uses a sourced route fare when one exists for the district, and otherwise a clearly labelled
 * estimate: BRTA's per-km rate for non-AC buses times ORBIT's approximate road distance.
 */
function estimateTransport(dest) {
  const route = COSTS.entries.find((e) => e.category === 'transport' && e.district === dest.district && e.id.startsWith('route_'));
  const out = { fromDhaka: true, distanceKm: dest.distanceKm, notes: [] };
  if (dest.distanceKm <= 10) { out.notes.push('Within Dhaka: use local transport.'); return out; }
  const est = { low: round10(dest.distanceKm * PER_KM.low), high: round10(dest.distanceKm * PER_KM.high) };
  out.busNonAc = route && route.value.bus_nonac_tk
    ? { oneWayTk: route.value.bus_nonac_tk, basis: 'BRTA chart (sourced)', confidence: route.confidence, sourceId: route.id }
    : { oneWayTkEstimate: est, basis: `BRTA non-AC rate Tk ${PER_KM.low}-${PER_KM.high} per km x about ${dest.distanceKm} km`, confidence: 'L' };
  if (route && route.value.train_tk) out.train = { oneWayTk: route.value.train_tk, confidence: route.confidence, sourceId: route.id };
  if (route && route.value.air_oneway_from_tk) out.air = { oneWayFromTk: route.value.air_oneway_from_tk, confidence: 'L', note: 'Dynamic fare' };
  out.notes.push('AC and premium coaches are not covered by the BRTA chart; expect noticeably higher fares, especially in peak season.');
  return out;
}

module.exports = { COSTS, PER_KM, estimateTransport };
