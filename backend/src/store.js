'use strict';
const fs = require('node:fs');
const path = require('node:path');

/** Tiny JSON-file database. Fine for a course prototype; swap for PostgreSQL before real use. */
class Store {
  constructor(file) {
    this.file = file;
    this.db = { users: {}, visits: {}, memory: {}, submissions: [], otps: {}, groups: {}, prices: [], advisories: [] };
    if (file && fs.existsSync(file)) {
      this.db = { ...this.db, ...JSON.parse(fs.readFileSync(file, 'utf8')) };
    }
  }
  save() {
    if (!this.file) return; // in-memory mode (tests)
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    const tmp = this.file + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(this.db));
    fs.renameSync(tmp, this.file);
  }
  findUserByIdentifier(identifier) {
    return Object.values(this.db.users).find((u) => u.identifier === identifier) || null;
  }
}
module.exports = { Store };
