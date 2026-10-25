'use strict';
const KINDS = ['bus', 'train', 'launch', 'stay', 'food', 'guide', 'entry'];

/** Median, ignoring the top and bottom 10% once there are 5 or more reports, so one wild entry cannot move it. */
function robustMedian(values) {
  const v = values.slice().sort((a, b) => a - b);
  const trim = v.length >= 5 ? Math.floor(v.length * 0.1) : 0;
  const w = v.slice(trim, v.length - trim);
  const mid = Math.floor(w.length / 2);
  return w.length % 2 ? w[mid] : Math.round((w[mid - 1] + w[mid]) / 2);
}

/** reports: [{destinationId, kind, amountBdt, createdAt}] -> { [destinationId]: { [kind]: {median, count, updatedAt} } } */
function aggregate(reports, now = Date.now(), maxAgeDays = 365) {
  const cutoff = now - maxAgeDays * 86400000;
  const groups = {};
  for (const r of reports) {
    if (new Date(r.createdAt).getTime() < cutoff) continue;
    ((groups[r.destinationId] ||= {})[r.kind] ||= []).push(r);
  }
  const out = {};
  for (const [dest, kinds] of Object.entries(groups)) {
    out[dest] = {};
    for (const [kind, list] of Object.entries(kinds)) {
      out[dest][kind] = { median: robustMedian(list.map((x) => x.amountBdt)), count: list.length, updatedAt: list.map((x) => x.createdAt).sort().pop() };
    }
  }
  return out;
}
module.exports = { KINDS, robustMedian, aggregate };
