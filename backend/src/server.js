'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Store } = require('./store');
const auth = require('./auth');
const planner = require('./planner');
const { runAgent } = require('./agent');
const brain = require('./brain');
const { haversineKm, CHECKIN_RADIUS_KM } = require('./geo');
const { computeBadges } = require('./badges');
const groupsLib = require('./groups');
const pricesLib = require('./prices');
const transport = require('./transport');
const { INTERESTS, DISTRICTS, BASE_DESTINATIONS, ACTIVITIES, BOUNDS } = require('./data');

const MAX_BODY = 64 * 1024;
const MEMORY_LIMIT = 50;
const clean = (s, max) => String(s ?? '').replace(/[\u0000-\u001f\u007f<>]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);

function createApp(opts = {}) {
  const env = opts.env || process.env;
  const store = opts.store || new Store(env.ORBIT_DATA || path.join(__dirname, '..', 'data', 'db.json'));
  let secret = env.ORBIT_SECRET;
  if (!secret) {
    secret = crypto.randomBytes(32).toString('hex');
    console.warn('[warn] ORBIT_SECRET is not set; logins will not survive a restart.');
  }
  const fetchImpl = opts.fetchImpl || fetch;
  const deliverOtp = opts.sendOtp || (async (identifier, code) => {
    if (env.ORBIT_DEV_OTP === '1') {
      console.log(`[dev otp] ${identifier}: ${code}`);
      return true;
    }
    // Production email verification uses Resend. Never claim a code was sent when no delivery
    // provider is configured. Phone verification needs an SMS provider and is intentionally
    // rejected until one is configured rather than silently losing the code.
    if (!identifier.includes('@') || !env.RESEND_API_KEY || !env.ORBIT_EMAIL_FROM) return false;
    try {
      const response = await fetchImpl('https://api.resend.com/emails', {
        method: 'POST',
        headers: { Authorization: `Bearer ${env.RESEND_API_KEY}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          from: env.ORBIT_EMAIL_FROM,
          to: [identifier],
          subject: 'Your ORBIT verification code',
          text: `Your ORBIT verification code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`
        }),
        signal: AbortSignal.timeout(15000)
      });
      return response.ok;
    } catch (_) {
      return false;
    }
  });
  const loginLimiter = new auth.RateLimiter(10, 15 * 60 * 1000);
  // Verification codes are short-lived and individually attempt-limited; also throttle
  // across re-issued codes so repeated signup attempts cannot bypass the per-code cap.
  const verifyLimiter = new auth.RateLimiter(10, 15 * 60 * 1000);
  const signupLimiter = new auth.RateLimiter(10, 60 * 60 * 1000);
  const agentLimiter = new auth.RateLimiter(20, 60 * 1000);
  const submitLimiter = new auth.RateLimiter(5, 24 * 3600 * 1000);

  const db = () => store.db;
  const send = (res, code, body, headers = {}) => {
    const data = typeof body === 'string' ? body : JSON.stringify(body);
    res.writeHead(code, { 'content-type': typeof body === 'string' ? 'text/html; charset=utf-8' : 'application/json; charset=utf-8', 'x-content-type-options': 'nosniff', 'cache-control': 'no-store', ...headers });
    res.end(data);
  };
  const readJson = (req) => new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > MAX_BODY) { reject(Object.assign(new Error('Payload too large'), { status: 413 })); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); } catch { reject(Object.assign(new Error('Invalid JSON'), { status: 400 })); } });
    req.on('error', reject);
  });

  const allDestinations = () => {
    const community = db().submissions.filter((s) => s.status === 'approved').map((s) => ({ ...s.spot, id: s.spot.id, community: true }));
    return [...BASE_DESTINATIONS, ...community];
  };
  const activitiesFor = () => ACTIVITIES;
  const qualityMap = () => {
    const by = {};
    for (const list of Object.values(db().visits)) for (const v of list) if (v.rating) (by[v.destinationId] ||= []).push(v.rating);
    return Object.fromEntries(Object.entries(by).map(([id, r]) => [id, planner.qualityFromRatings(r)]));
  };
  const publicUser = (u) => ({ id: u.id, displayName: u.displayName, identifierType: u.identifierType, identifier: u.identifier, verified: u.verified, consent: u.consent, createdAt: u.createdAt });
  const tokenFor = (u) => auth.signToken({ sub: u.id }, secret);
  const userFrom = (req) => {
    const h = req.headers.authorization || '';
    const payload = h.startsWith('Bearer ') ? auth.verifyToken(h.slice(7), secret) : null;
    return payload ? db().users[payload.sub] || null : null;
  };
  const staticPage = (res, file) => {
    try { send(res, 200, fs.readFileSync(path.join(__dirname, '..', 'web', file), 'utf8'), { 'cache-control': 'public, max-age=300' }); }
    catch { send(res, 404, { error: 'not_found' }); }
  };

  const routes = [];
  const route = (method, pattern, handler, needsAuth = true) => routes.push({ method, re: new RegExp('^' + pattern + '$'), handler, needsAuth });

  // ---------- auth ----------
  route('POST', '/api/auth/signup', async (req, res, ctx) => {
    if (!signupLimiter.allow(req.socket.remoteAddress || 'x')) return send(res, 429, { error: 'too_many_requests' });
    const b = ctx.body;
    const id = auth.normalizeIdentifier(b.identifier);
    if (!id) return send(res, 400, { error: 'invalid_identifier', message: 'Enter a valid email or a Bangladesh mobile number.' });
    const pw = auth.passwordProblem(b.password, id.value);
    if (pw) return send(res, 400, { error: 'weak_password', message: pw });
    const c = b.consent || {};
    if (c.terms !== true || c.privacy !== true) return send(res, 400, { error: 'consent_required', message: 'You must accept the Terms and the Privacy Policy to create an account.' });
    const displayName = clean(b.displayName, 30);
    if (displayName.length < 2) return send(res, 400, { error: 'invalid_name', message: 'Choose a display name of 2 to 30 characters.' });
    let user = store.findUserByIdentifier(id.value);
    if (user && user.verified) return send(res, 409, { error: 'account_exists' });
    if (!user) {
      user = {
        id: crypto.randomUUID(), identifier: id.value, identifierType: id.type, displayName, passwordHash: auth.hashPassword(b.password), verified: false, createdAt: new Date().toISOString(),
        consent: { termsVersion: auth.TERMS_VERSION, privacyVersion: auth.PRIVACY_VERSION, acceptedAt: new Date().toISOString(), ai: c.ai === true, location: c.location === true, leaderboard: c.leaderboard === true },
      };
      db().users[user.id] = user;
    }
    const otp = auth.newOtp();
    db().otps[user.id] = otp;
    store.save();
    const delivered = await deliverOtp(id.value, otp.code);
    if (delivered === false) {
      return send(res, 503, {
        error: 'otp_delivery_not_configured',
        message: id.type === 'phone'
          ? 'SMS verification is not configured on this server yet. Use an email address or contact the app administrator.'
          : 'Email verification is not configured or the email provider rejected the message. Please try again later.'
      });
    }
    send(res, 201, { status: 'verification_required', ...(env.ORBIT_DEV_OTP === '1' ? { devCode: otp.code } : {}) });
  }, false);

  route('POST', '/api/auth/verify', async (req, res, ctx) => {
    const id = auth.normalizeIdentifier(ctx.body.identifier);
    const key = (id ? id.value : 'x') + '|' + (req.socket.remoteAddress || '');
    if (!verifyLimiter.allow(key)) return send(res, 429, { error: 'too_many_requests' });
    const user = id && store.findUserByIdentifier(id.value);
    if (!user || !auth.checkOtp(db().otps[user.id], ctx.body.code)) { store.save(); return send(res, 400, { error: 'invalid_code' }); }
    user.verified = true; delete db().otps[user.id]; store.save();
    send(res, 200, { token: tokenFor(user), user: publicUser(user) });
  }, false);

  route('POST', '/api/auth/login', async (req, res, ctx) => {
    const id = auth.normalizeIdentifier(ctx.body.identifier);
    const key = (id ? id.value : 'x') + '|' + (req.socket.remoteAddress || '');
    if (!loginLimiter.allow(key)) return send(res, 429, { error: 'too_many_requests' });
    const user = id && store.findUserByIdentifier(id.value);
    const ok = user && typeof ctx.body.password === 'string' && auth.verifyPassword(ctx.body.password, user.passwordHash);
    if (!ok) return send(res, 401, { error: 'invalid_credentials' });
    if (!user.verified) return send(res, 403, { error: 'not_verified' });
    send(res, 200, { token: tokenFor(user), user: publicUser(user) });
  }, false);

  // ---------- account & privacy ----------
  route('GET', '/api/me', async (req, res, ctx) => send(res, 200, { user: publicUser(ctx.user) }));

  route('PUT', '/api/me/consent', async (req, res, ctx) => {
    const c = ctx.body, cur = ctx.user.consent;
    for (const k of ['ai', 'location', 'leaderboard']) if (typeof c[k] === 'boolean') cur[k] = c[k];
    store.save(); send(res, 200, { consent: cur });
  });

  route('GET', '/api/me/export', async (req, res, ctx) => {
    const u = ctx.user;
    send(res, 200, { account: publicUser(u), visits: db().visits[u.id] || [], memory: db().memory[u.id] || [], submissions: db().submissions.filter((s) => s.authorId === u.id).map((s) => ({ id: s.id, status: s.status, spot: s.spot })), priceReports: db().prices.filter((p) => p.userId === u.id), groups: Object.values(db().groups).filter((g) => g.members.some((m) => m.userId === u.id)).map((g) => ({ id: g.id, name: g.name })) });
  });

  route('DELETE', '/api/me', async (req, res, ctx) => {
    const u = ctx.user;
    delete db().users[u.id]; delete db().visits[u.id]; delete db().memory[u.id]; delete db().otps[u.id];
    for (const s of db().submissions) if (s.authorId === u.id) s.authorId = null; // keep approved spots, drop the link to the person
    db().prices = db().prices.filter((p) => p.userId !== u.id);
    for (const g of Object.values(db().groups)) for (const m of g.members) if (m.userId === u.id) { m.userId = null; m.left = true; m.displayName = 'Former member'; }
    store.save(); send(res, 200, { deleted: true });
  });

  // ---------- destinations & community ----------
  route('GET', '/api/destinations', async (req, res) => send(res, 200, { destinations: allDestinations(), districts: DISTRICTS }), false);

  route('POST', '/api/destinations/submit', async (req, res, ctx) => {
    if (!submitLimiter.allow(ctx.user.id)) return send(res, 429, { error: 'daily_limit', message: 'You can submit up to 5 spots per day.' });
    const b = ctx.body;
    const name = clean(b.name, 60), description = clean(b.description, 300);
    if (name.length < 3) return send(res, 400, { error: 'invalid_name' });
    if (!DISTRICTS.includes(b.district)) return send(res, 400, { error: 'invalid_district' });
    const tags = (Array.isArray(b.tags) ? b.tags : []).filter((t) => INTERESTS.includes(t)).slice(0, 3);
    if (tags.length === 0) return send(res, 400, { error: 'invalid_tags', message: 'Pick 1 to 3 interest tags.' });
    const cost = Number.isFinite(b.costPerDayBdt) ? Math.round(b.costPerDayBdt) : NaN;
    if (!(cost >= 0 && cost <= 20000)) return send(res, 400, { error: 'invalid_cost' });
    let lat = null, lon = null;
    if (b.latitude != null || b.longitude != null) {
      lat = Number(b.latitude); lon = Number(b.longitude);
      if (!(lat >= BOUNDS.minLat && lat <= BOUNDS.maxLat && lon >= BOUNDS.minLon && lon <= BOUNDS.maxLon)) return send(res, 400, { error: 'invalid_coordinates', message: 'Coordinates must be inside Bangladesh.' });
    }
    const dup = [...BASE_DESTINATIONS, ...db().submissions.filter((s) => s.status !== 'rejected').map((s) => s.spot)].some((d) => d.district === b.district && d.name.toLowerCase() === name.toLowerCase());
    if (dup) return send(res, 409, { error: 'duplicate' });
    const nextId = 10000 + db().submissions.length + 1;
    const base = BASE_DESTINATIONS.find((d) => d.district === b.district);
    const spot = { id: nextId, name, district: b.district, division: base ? base.division : '', latitude: lat ?? (base ? base.latitude : 23.8), longitude: lon ?? (base ? base.longitude : 90.4), tags, distanceKm: base ? base.distanceKm : 200, costPerDayBdt: cost, recommendedDays: 1, bestMonths: [10, 11, 12, 1, 2, 3], blurb: description };
    const sub = { id: crypto.randomUUID(), authorId: ctx.user.id, status: 'pending', createdAt: new Date().toISOString(), spot };
    db().submissions.push(sub); store.save();
    send(res, 201, { id: sub.id, status: 'pending', message: 'Thanks. Your spot will appear after review.' });
  });

  route('POST', '/api/admin/submissions/([0-9a-f-]{36})', async (req, res, ctx) => {
    const t = env.ORBIT_ADMIN_TOKEN;
    const given = String(req.headers['x-admin-token'] || '');
    if (!t || given.length !== t.length || !crypto.timingSafeEqual(Buffer.from(given), Buffer.from(t))) return send(res, 403, { error: 'forbidden' });
    const sub = db().submissions.find((s) => s.id === ctx.params[0]);
    if (!sub) return send(res, 404, { error: 'not_found' });
    if (!['approve', 'reject'].includes(ctx.body.action)) return send(res, 400, { error: 'invalid_action' });
    sub.status = ctx.body.action === 'approve' ? 'approved' : 'rejected'; store.save();
    send(res, 200, { id: sub.id, status: sub.status });
  }, false);

  // ---------- visits & leaderboard ----------
  route('GET', '/api/visits', async (req, res, ctx) => send(res, 200, { visits: db().visits[ctx.user.id] || [] }));

  route('POST', '/api/visits', async (req, res, ctx) => {
    const b = ctx.body;
    const dest = allDestinations().find((d) => d.id === b.destinationId);
    if (!dest) return send(res, 400, { error: 'unknown_destination' });
    const list = (db().visits[ctx.user.id] ||= []);
    const rating = Number.isInteger(b.rating) && b.rating >= 1 && b.rating <= 5 ? b.rating : null;
    // A GPS check-in counts as verified only if the phone reports a position near the spot. Spot positions are district-level,
    // and a determined cheater can fake a location, so this raises the bar but is not proof.
    const lat = Number(b.lat), lon = Number(b.lon);
    const located = b.source === 'gps' && b.mock !== true && lat >= BOUNDS.minLat && lat <= BOUNDS.maxLat && lon >= BOUNDS.minLon && lon <= BOUNDS.maxLon;
    const verified = located && haversineKm(lat, lon, dest.latitude, dest.longitude) <= CHECKIN_RADIUS_KM;
    const rec = { destinationId: b.destinationId, visitedAt: new Date().toISOString(), rating, note: clean(b.note, 300), source: verified ? 'gps' : 'self', verified };
    const i = list.findIndex((v) => v.destinationId === b.destinationId);
    if (i >= 0) { const keep = list[i].verified === true || verified; list[i] = { ...list[i], ...rec, visitedAt: list[i].visitedAt, verified: keep, source: keep ? 'gps' : 'self' }; }
    else { if (list.length >= 300) return send(res, 400, { error: 'limit_reached' }); list.push(rec); }
    store.save(); send(res, 200, { visits: list });
  });

  route('DELETE', '/api/visits/(\\d+)', async (req, res, ctx) => {
    const id = Number(ctx.params[0]);
    db().visits[ctx.user.id] = (db().visits[ctx.user.id] || []).filter((v) => v.destinationId !== id);
    store.save(); send(res, 200, { visits: db().visits[ctx.user.id] });
  });

  route('GET', '/api/leaderboard', async (req, res, ctx) => {
    const dests = new Map(allDestinations().map((d) => [d.id, d]));
    const rows = Object.values(db().users).filter((u) => u.verified && u.consent.leaderboard).map((u) => {
      const list = db().visits[u.id] || [];
      const spots = new Set(list.map((v) => v.destinationId));
      const districts = new Set([...spots].map((id) => dests.get(id)?.district).filter(Boolean));
      const verified = list.filter((v) => v.verified === true).length;
      return { userId: u.id, displayName: u.displayName, spots: spots.size, districts: districts.size, verified };
    }).sort((a, b) => b.verified - a.verified || b.spots - a.spots || b.districts - a.districts || a.displayName.localeCompare(b.displayName));
    rows.forEach((r, i) => { r.rank = i + 1; });
    const me = rows.find((r) => r.userId === ctx.user.id) || null;
    send(res, 200, { top: rows.slice(0, 50).map(({ userId, ...r }) => ({ ...r, isMe: userId === ctx.user.id })), me: me && { rank: me.rank, spots: me.spots, districts: me.districts, verified: me.verified }, optedIn: !!ctx.user.consent.leaderboard });
  });


  // ---------- badges ----------
  route('GET', '/api/badges', async (req, res, ctx) => send(res, 200, { badges: computeBadges(allDestinations(), db().visits[ctx.user.id] || []) }));

  // ---------- community price reports ----------
  const priceLimiter = new auth.RateLimiter(20, 24 * 3600 * 1000);
  route('GET', '/api/prices', async (req, res) => send(res, 200, { prices: pricesLib.aggregate(db().prices), kinds: pricesLib.KINDS }), false);
  route('POST', '/api/prices', async (req, res, ctx) => {
    const b = ctx.body;
    if (!priceLimiter.allow(ctx.user.id)) return send(res, 429, { error: 'daily_limit' });
    if (!allDestinations().some((d) => d.id === b.destinationId)) return send(res, 400, { error: 'unknown_destination' });
    if (!pricesLib.KINDS.includes(b.kind)) return send(res, 400, { error: 'invalid_kind' });
    const amount = Number.isInteger(b.amountBdt) ? b.amountBdt : NaN;
    if (!(amount >= 1 && amount <= 1000000)) return send(res, 400, { error: 'invalid_amount' });
    const rec = { id: crypto.randomUUID(), userId: ctx.user.id, destinationId: b.destinationId, kind: b.kind, amountBdt: amount, note: clean(b.note, 100), createdAt: new Date().toISOString() };
    const i = db().prices.findIndex((p) => p.userId === ctx.user.id && p.destinationId === rec.destinationId && p.kind === rec.kind);
    if (i >= 0) db().prices[i] = rec; else db().prices.push(rec);   // one report per traveller, spot and kind: the latest wins
    store.save();
    send(res, 201, { prices: pricesLib.aggregate(db().prices) });
  });

  // ---------- researched reference data (sourced, dated, with a confidence level) ----------
  route('GET', '/api/travel-costs', async (req, res) => send(res, 200, transport.COSTS), false);
  route('GET', '/api/transport/(\\d+)', async (req, res, ctx) => {
    const d = allDestinations().find((x) => x.id === Number(ctx.params[0]));
    if (!d) return send(res, 404, { error: 'unknown_destination' });
    send(res, 200, { destinationId: d.id, name: d.name, ...transport.estimateTransport(d) });
  }, false);

  // ---------- advisories (published by the operator; the app shows them and the guide mentions them) ----------
  const DIVISIONS = [...new Set(BASE_DESTINATIONS.map((d) => d.division))];
  const adminOk = (req) => { const t = env.ORBIT_ADMIN_TOKEN, g = String(req.headers['x-admin-token'] || ''); return !!t && g.length === t.length && crypto.timingSafeEqual(Buffer.from(g), Buffer.from(t)); };
  const activeAdvisories = () => db().advisories.filter((a) => new Date(a.expiresAt).getTime() > Date.now());
  route('GET', '/api/advisories', async (req, res) => send(res, 200, { advisories: activeAdvisories() }), false);
  route('POST', '/api/admin/advisories', async (req, res, ctx) => {
    if (!adminOk(req)) return send(res, 403, { error: 'forbidden' });
    const b = ctx.body;
    if (!['national', 'division', 'district'].includes(b.scope)) return send(res, 400, { error: 'invalid_scope' });
    if (b.scope === 'division' && !DIVISIONS.includes(b.target)) return send(res, 400, { error: 'invalid_target' });
    if (b.scope === 'district' && !DISTRICTS.includes(b.target)) return send(res, 400, { error: 'invalid_target' });
    if (!['info', 'warning', 'danger'].includes(b.severity)) return send(res, 400, { error: 'invalid_severity' });
    const title = clean(b.title, 80), text = clean(b.text, 300);
    if (title.length < 3) return send(res, 400, { error: 'invalid_title' });
    const hours = Number.isInteger(b.hours) && b.hours >= 1 && b.hours <= 720 ? b.hours : 72;
    const adv = { id: crypto.randomUUID(), scope: b.scope, target: b.scope === 'national' ? null : b.target, severity: b.severity, title, text, createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + hours * 3600000).toISOString() };
    db().advisories.push(adv); store.save();
    send(res, 201, { advisory: adv });
  }, false);
  route('DELETE', '/api/admin/advisories/([0-9a-f-]{36})', async (req, res, ctx) => {
    if (!adminOk(req)) return send(res, 403, { error: 'forbidden' });
    db().advisories = db().advisories.filter((a) => a.id !== ctx.params[0]); store.save();
    send(res, 200, { deleted: true });
  }, false);

  // ---------- group trips and expense splitting ----------
  const memberOf = (g, userId) => g.members.find((m) => m.userId === userId && !m.left);
  const groupView = (g, userId) => {
    const nameOf = Object.fromEntries(g.members.map((m) => [m.mid, m.displayName]));
    const net = groupsLib.balances(g.members.map((m) => m.mid), g.expenses);
    return {
      id: g.id, name: g.name, code: g.code,
      members: g.members.map((m) => ({ mid: m.mid, displayName: m.displayName, left: !!m.left, isMe: m.userId === userId })),
      expenses: g.expenses.map((e) => ({ id: e.id, description: e.description, amount: e.amount, paidBy: e.paidBy, paidByName: nameOf[e.paidBy], shares: e.shares, createdAt: e.createdAt })),
      balances: Object.entries(net).map(([mid, amount]) => ({ mid, displayName: nameOf[mid], amount })),
      settlements: groupsLib.settlements(net).map((t) => ({ ...t, fromName: nameOf[t.from], toName: nameOf[t.to] })),
      total: g.expenses.reduce((s, e) => s + e.amount, 0),
      plan: g.plan || null,
    };
  };
  const groupFor = (ctx, res) => {
    const g = db().groups[ctx.params[0]];
    if (!g || !memberOf(g, ctx.user.id)) { send(res, 404, { error: 'not_found' }); return null; }
    return g;
  };
  const myGroups = (userId) => Object.values(db().groups).filter((g) => memberOf(g, userId));
  const makeCode = () => { for (;;) { let c = ''; for (let i = 0; i < 6; i++) c += groupsLib.CODE_ALPHABET[crypto.randomInt(groupsLib.CODE_ALPHABET.length)]; if (!Object.values(db().groups).some((g) => g.code === c)) return c; } };

  route('GET', '/api/groups', async (req, res, ctx) => send(res, 200, { groups: myGroups(ctx.user.id).map((g) => ({ id: g.id, name: g.name, code: g.code, memberCount: g.members.filter((m) => !m.left).length })) }));

  route('POST', '/api/groups', async (req, res, ctx) => {
    const name = clean(ctx.body.name, 40);
    if (name.length < 2) return send(res, 400, { error: 'invalid_name' });
    if (myGroups(ctx.user.id).length >= 10) return send(res, 400, { error: 'group_limit' });
    const g = { id: crypto.randomUUID(), name, code: makeCode(), createdAt: new Date().toISOString(), members: [{ mid: crypto.randomUUID(), userId: ctx.user.id, displayName: ctx.user.displayName }], expenses: [], plan: null };
    db().groups[g.id] = g; store.save();
    send(res, 201, { group: groupView(g, ctx.user.id) });
  });

  route('POST', '/api/groups/join', async (req, res, ctx) => {
    const code = String(ctx.body.code || '').trim().toUpperCase();
    const g = Object.values(db().groups).find((x) => x.code === code);
    if (!g) return send(res, 404, { error: 'invalid_code', message: 'No group has that code.' });
    if (memberOf(g, ctx.user.id)) return send(res, 200, { group: groupView(g, ctx.user.id) });
    if (g.members.filter((m) => !m.left).length >= 20) return send(res, 400, { error: 'group_full' });
    if (myGroups(ctx.user.id).length >= 10) return send(res, 400, { error: 'group_limit' });
    g.members.push({ mid: crypto.randomUUID(), userId: ctx.user.id, displayName: ctx.user.displayName }); store.save();
    send(res, 200, { group: groupView(g, ctx.user.id) });
  });

  route('GET', '/api/groups/([0-9a-f-]{36})', async (req, res, ctx) => { const g = groupFor(ctx, res); if (g) send(res, 200, { group: groupView(g, ctx.user.id) }); });

  route('POST', '/api/groups/([0-9a-f-]{36})/expenses', async (req, res, ctx) => {
    const g = groupFor(ctx, res); if (!g) return;
    const b = ctx.body, active = g.members.filter((m) => !m.left).map((m) => m.mid);
    const amount = Number.isInteger(b.amount) ? b.amount : NaN;
    if (!(amount >= 1 && amount <= 10000000)) return send(res, 400, { error: 'invalid_amount' });
    const description = clean(b.description, 60);
    if (description.length < 2) return send(res, 400, { error: 'invalid_description' });
    if (!active.includes(b.paidBy)) return send(res, 400, { error: 'invalid_payer' });
    const split = Array.isArray(b.split) && b.split.length ? [...new Set(b.split)] : active;
    if (!split.every((id) => active.includes(id))) return send(res, 400, { error: 'invalid_split' });
    if (g.expenses.length >= 300) return send(res, 400, { error: 'limit_reached' });
    g.expenses.push({ id: crypto.randomUUID(), description, amount, paidBy: b.paidBy, shares: groupsLib.equalShares(amount, split), createdBy: ctx.user.id, createdAt: new Date().toISOString() });
    store.save(); send(res, 201, { group: groupView(g, ctx.user.id) });
  });

  route('DELETE', '/api/groups/([0-9a-f-]{36})/expenses/([0-9a-f-]{36})', async (req, res, ctx) => {
    const g = groupFor(ctx, res); if (!g) return;
    const e = g.expenses.find((x) => x.id === ctx.params[1]);
    if (!e) return send(res, 404, { error: 'not_found' });
    const isCreator = g.members[0] && g.members[0].userId === ctx.user.id;   // whoever started the group may tidy up
    if (e.createdBy !== ctx.user.id && !isCreator) return send(res, 403, { error: 'forbidden', message: 'Only the person who added an expense can remove it.' });
    g.expenses = g.expenses.filter((x) => x.id !== e.id); store.save();
    send(res, 200, { group: groupView(g, ctx.user.id) });
  });

  route('POST', '/api/groups/([0-9a-f-]{36})/plan', async (req, res, ctx) => {
    const g = groupFor(ctx, res); if (!g) return;
    const p = ctx.body.plan || {};
    const items = (Array.isArray(p.items) ? p.items : []).slice(0, 42).map((i) => ({ day: Math.min(14, Math.max(1, Math.round(Number(i.day)) || 1)), hour: Math.min(23, Math.max(0, Math.round(Number(i.hour)) || 9)), name: clean(i.name, 80) })).filter((i) => i.name);
    const name = clean(p.destinationName, 60);
    if (!name) return send(res, 400, { error: 'invalid_plan' });
    g.plan = { destinationName: name, days: Math.min(14, Math.max(1, Math.round(Number(p.days)) || 1)), tier: ['budget', 'value', 'fancy'].includes(p.tier) ? p.tier : 'value', items, sharedBy: ctx.user.displayName, sharedAt: new Date().toISOString() };
    store.save(); send(res, 200, { group: groupView(g, ctx.user.id) });
  });

  route('POST', '/api/groups/([0-9a-f-]{36})/leave', async (req, res, ctx) => {
    const g = groupFor(ctx, res); if (!g) return;
    const m = memberOf(g, ctx.user.id);
    m.left = true; m.userId = null; m.displayName = 'Former member';
    if (!g.members.some((x) => !x.left)) delete db().groups[g.id];
    store.save(); send(res, 200, { left: true });
  });

  // ---------- memory & agent ----------
  route('GET', '/api/memory', async (req, res, ctx) => send(res, 200, { notes: db().memory[ctx.user.id] || [] }));
  route('DELETE', '/api/memory', async (req, res, ctx) => { db().memory[ctx.user.id] = []; store.save(); send(res, 200, { notes: [] }); });
  route('DELETE', '/api/memory/([0-9a-f-]{36})', async (req, res, ctx) => {
    db().memory[ctx.user.id] = (db().memory[ctx.user.id] || []).filter((m) => m.id !== ctx.params[0]); store.save();
    send(res, 200, { notes: db().memory[ctx.user.id] });
  });

  route('POST', '/api/plan', async (req, res, ctx) => {
    const b = ctx.body;
    const q = { budget: Number(b.budget) || 30000, days: Math.min(14, Math.max(1, Math.round(b.days) || 3)), travellers: Math.min(30, Math.max(1, Math.round(b.travellers) || 2)), month: Math.min(12, Math.max(1, Math.round(b.month) || new Date().getMonth() + 1)), interests: (b.interests || []).filter((i) => INTERESTS.includes(i)), tier: planner.normalizeTier(b.tier) };
    const rows = planner.rank(allDestinations(), q, { quality: qualityMap() }).slice(0, 8);
    send(res, 200, { query: q, results: rows.map((r) => ({ id: r.destination.id, name: r.destination.name, district: r.destination.district, score: r.score.total, estimatedCostBdt: r.score.estimatedCostBdt, affordable: r.score.affordable })) });
  }, false);

  // Optional photo generation. Uses a server-side key only; never ship provider secrets in the app.
  route('POST', '/api/agent/image', async (req, res, ctx) => {
    const u = ctx.user;
    if (!u.consent.ai) return send(res, 403, { error: 'ai_consent_required', message: 'Enable AI guide consent in Account settings to generate images.' });
    if (!env.OPENAI_API_KEY) return send(res, 503, { error: 'image_generation_not_configured', message: 'Image generation is not configured on this server yet.' });
    const prompt = clean(ctx.body.prompt, 1200);
    if (!prompt) return send(res, 400, { error: 'empty_prompt' });
    if (!agentLimiter.allow(`image:${u.id}`)) return send(res, 429, { error: 'too_many_requests' });
    try {
      const upstream = await fetchImpl('https://api.openai.com/v1/images/generations', {
        method: 'POST',
        headers: { Authorization: `Bearer ${env.OPENAI_API_KEY}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: env.ORBIT_IMAGE_MODEL || 'gpt-image-1', prompt: `Create a high-quality travel image for ORBIT, a Bangladesh travel companion. Be visually compelling and do not invent factual labels, signs, or maps. Prompt: ${prompt}`, size: '1024x1024', n: 1 }),
        signal: AbortSignal.timeout(45000)
      });
      const payload = await upstream.json().catch(() => ({}));
      if (!upstream.ok) return send(res, upstream.status === 429 ? 429 : 502, { error: 'image_provider_error', message: upstream.status === 429 ? 'Image generation is busy. Please wait a moment and try again.' : 'The image provider could not complete this request. Check server configuration and try again.' });
      const image = payload.data?.[0];
      if (!image?.url && !image?.b64_json) return send(res, 502, { error: 'empty_image_result' });
      send(res, 200, { url: image.url || null, b64: image.b64_json || null, revisedPrompt: image.revised_prompt || prompt });
    } catch (_) { send(res, 502, { error: 'image_provider_unavailable' }); }
  });

  route('POST', '/api/agent/chat', async (req, res, ctx) => {
    const u = ctx.user;
    const message = clean(ctx.body.message, 1000);
    if (!message) return send(res, 400, { error: 'empty_message' });
    if (!agentLimiter.allow(u.id)) return send(res, 429, { error: 'too_many_requests' });
    // Cloud AI is an optional extra, off unless the operator sets ORBIT_CLOUD_AI=1 and a key. The default agent is ORBIT Brain.
    const cloud = env.ORBIT_CLOUD_AI === '1' && !!env.ANTHROPIC_API_KEY;
    if (cloud && !u.consent.ai) return send(res, 403, { error: 'ai_consent_required', message: 'Turn on "AI guide" in Account > Data and permissions to use the cloud guide.' });

    let location = null;
    const loc = ctx.body.location;
    if (u.consent.location && loc && Number(loc.lat) >= BOUNDS.minLat && Number(loc.lat) <= BOUNDS.maxLat && Number(loc.lon) >= BOUNDS.minLon && Number(loc.lon) <= BOUNDS.maxLon) {
      location = { lat: Number(loc.lat), lon: Number(loc.lon), targetId: Number.isInteger(loc.targetId) ? loc.targetId : undefined };
    }
    const saveMemory = (text) => { const list = (db().memory[u.id] ||= []); if (list.length >= MEMORY_LIMIT) return false; list.push({ id: crypto.randomUUID(), text, createdAt: new Date().toISOString() }); store.save(); return true; };

    if (!cloud) {
      const brainCtx = { month: new Date().getMonth() + 1, destinations: allDestinations(), activities: activitiesFor(), visits: db().visits[u.id] || [], memory: db().memory[u.id] || [], quality: qualityMap(), location };
      const state = ctx.body.state && typeof ctx.body.state === 'object' ? ctx.body.state : {};
      const r = brain.respond(message, brainCtx, state);
      for (const a of r.actions) {
        if (a.type === 'remember') saveMemory(a.text);
        if (a.type === 'forget_all') db().memory[u.id] = [];
      }
      store.save();
      return send(res, 200, { reply: r.reply, plan: r.plan, state: r.state, engine: 'native' });
    }

    const history = (Array.isArray(ctx.body.history) ? ctx.body.history : []).filter((m) => m && (m.role === 'user' || m.role === 'assistant') && typeof m.content === 'string').slice(-10).map((m) => ({ role: m.role, content: clean(m.content, 2000) }));
    const locationNote = location ? `${location.lat.toFixed(3)}, ${location.lon.toFixed(3)} (approximate)` + (location.targetId ? `; heading to destination id ${location.targetId}` : '') : '';
    const context = {
      month: new Date().getMonth() + 1, destinations: allDestinations(), activities: activitiesFor(), visits: db().visits[u.id] || [], memory: db().memory[u.id] || [], quality: qualityMap(), lastPlan: null, saveMemory,
    };
    try {
      const out = await runAgent({ apiKey: env.ANTHROPIC_API_KEY, model: env.ANTHROPIC_MODEL || 'claude-sonnet-5-5', history, userMessage: message, context, locationNote, fetchImpl });
      send(res, 200, { ...out, engine: 'cloud' });
    } catch (e) {
      send(res, 502, { error: 'ai_error', message: 'The AI guide could not answer right now. Try again shortly.' });
    }
  });

  // ---------- handler ----------
  return async (req, res) => {
    try {
      const url = new URL(req.url, 'http://x');
      const p = url.pathname;
      if (req.method === 'GET' && p === '/health') return send(res, 200, { ok: true });
      if (req.method === 'GET' && p === '/privacy') return staticPage(res, 'privacy.html');
      if (req.method === 'GET' && p === '/terms') return staticPage(res, 'terms.html');
      for (const r of routes) {
        if (r.method !== req.method) continue;
        const m = p.match(r.re);
        if (!m) continue;
        const ctx = { params: m.slice(1), body: {}, user: null };
        if (r.needsAuth) { ctx.user = userFrom(req); if (!ctx.user) return send(res, 401, { error: 'unauthorized' }); }
        if (req.method === 'POST' || req.method === 'PUT') ctx.body = await readJson(req);
        return await r.handler(req, res, ctx);
      }
      send(res, 404, { error: 'not_found' });
    } catch (e) {
      send(res, e.status || 500, { error: e.status ? e.message : 'server_error' });
    }
  };
}

if (require.main === module) {
  const port = Number(process.env.PORT) || 8080;
  http.createServer(createApp()).listen(port, () => console.log(`ORBIT backend listening on :${port}`));
}
module.exports = { createApp };
