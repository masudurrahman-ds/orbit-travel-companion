'use strict';
/** Achievement badges, computed from a traveller's visits. Mirrored in Badges.kt and Badges.swift. */
const DEFS = [
  { id: 'first_step', title: 'First Step', description: 'Visit your first spot', target: 1, count: (c) => c.spots },
  { id: 'explorer_10', title: 'Explorer', description: 'Visit 10 spots', target: 10, count: (c) => c.spots },
  { id: 'district_5', title: 'District Hopper', description: 'Visit 5 districts', target: 5, count: (c) => c.districts },
  { id: 'district_16', title: 'Quarter Way', description: 'Visit 16 of 64 districts', target: 16, count: (c) => c.districts },
  { id: 'half_way', title: 'Half Way', description: 'Visit 32 of 64 districts', target: 32, count: (c) => c.districts },
  { id: 'all_divisions', title: 'All 8 Divisions', description: 'Visit a spot in every division', target: 8, count: (c) => c.divisions },
  { id: 'beach_lover', title: 'Beach Lover', description: 'Visit 5 beach spots', target: 5, count: (c) => c.tags.beach || 0 },
  { id: 'hill_explorer', title: 'Hill Explorer', description: 'Visit 5 hill spots', target: 5, count: (c) => c.tags.hills || 0 },
  { id: 'heritage_hunter', title: 'Heritage Hunter', description: 'Visit 8 culture spots', target: 8, count: (c) => c.tags.culture || 0 },
  { id: 'nature_walker', title: 'Nature Walker', description: 'Visit 8 nature spots', target: 8, count: (c) => c.tags.nature || 0 },
  { id: 'foodie_trail', title: 'Foodie Trail', description: 'Visit 5 food spots', target: 5, count: (c) => c.tags.food || 0 },
  { id: 'adventurer', title: 'Adventurer', description: 'Visit 4 adventure spots', target: 4, count: (c) => c.tags.adventure || 0 },
  { id: 'verified_5', title: 'On the Ground', description: '5 GPS-verified check-ins', target: 5, count: (c) => c.verified },
  { id: 'storyteller', title: 'Storyteller', description: 'Write notes on 3 visits', target: 3, count: (c) => c.notes },
];

function computeBadges(destinations, visits) {
  const byId = new Map(destinations.map((d) => [d.id, d]));
  const seen = new Map();
  for (const v of visits) if (byId.has(v.destinationId)) seen.set(v.destinationId, v);   // one row per spot
  const c = { spots: seen.size, districts: new Set(), divisions: new Set(), tags: {}, verified: 0, notes: 0 };
  for (const [id, v] of seen) {
    const d = byId.get(id);
    c.districts.add(d.district); if (d.division) c.divisions.add(d.division);
    for (const t of d.tags) c.tags[t] = (c.tags[t] || 0) + 1;
    if (v.verified === true || v.source === 'gps') c.verified += 1;
    if (v.note && String(v.note).trim()) c.notes += 1;
  }
  c.districts = c.districts.size; c.divisions = c.divisions.size;
  return DEFS.map((b) => { const progress = Math.min(b.count(c), b.target); return { id: b.id, title: b.title, description: b.description, progress, target: b.target, earned: progress >= b.target }; });
}
module.exports = { computeBadges, DEFS };
