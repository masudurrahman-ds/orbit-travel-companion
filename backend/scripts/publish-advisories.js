'use strict';
// Usage: ORBIT_URL=https://your-server ORBIT_ADMIN_TOKEN=... node scripts/publish-advisories.js [file.json]
// Publishes advisories from a JSON file. Review the text and sources first: these reach every traveller.
const fs = require('node:fs');
const path = require('node:path');

async function main() {
  const base = (process.env.ORBIT_URL || 'http://localhost:8080').replace(/\/$/, '');
  const token = process.env.ORBIT_ADMIN_TOKEN;
  if (!token) { console.error('Set ORBIT_ADMIN_TOKEN.'); process.exit(1); }
  const file = process.argv[2] || path.join(__dirname, '..', 'data', 'advisories.seed.json');
  const list = JSON.parse(fs.readFileSync(file, 'utf8'));
  const existing = (await (await fetch(base + '/api/advisories')).json()).advisories || [];
  for (const a of list) {
    if (existing.some((e) => e.title === a.title && e.target === a.target)) { console.log('skip (already live):', a.title, a.target || ''); continue; }
    const res = await fetch(base + '/api/admin/advisories', { method: 'POST', headers: { 'content-type': 'application/json', 'x-admin-token': token }, body: JSON.stringify(a) });
    console.log(res.status, a.title, a.target || '');
  }
}
main().catch((e) => { console.error(e.message); process.exit(1); });
