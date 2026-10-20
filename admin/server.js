'use strict';

const http = require('node:http');
const net = require('node:net');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { promisify } = require('node:util');
const { LogReader, SharedSnapshot } = require('./runtime');
const scrypt = promisify(crypto.scrypt);

const PORT = Number(process.env.PORT || 3000);
const ORIGIN = process.env.PUBLIC_ORIGIN || 'http://localhost:3000';
const STATE_DIR = process.env.STATE_DIR || path.join(__dirname, 'state');
const MC_DATA_DIR = process.env.MC_DATA_DIR || path.join(__dirname, '..', 'data');
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(__dirname, '..', 'backups');
const JOBS_DIR = process.env.JOBS_DIR || path.join(STATE_DIR, 'jobs');
const CUSTOM_ITEMS_DIR = process.env.CUSTOM_ITEMS_DIR || path.join(STATE_DIR, 'custom-items');
const TOWER_SCHEMATICS_DIR = process.env.TOWER_SCHEMATICS_DIR || path.join(STATE_DIR, 'tower-schematics');
const RCON_HOST = process.env.RCON_HOST || '127.0.0.1';
const RCON_PORT = Number(process.env.RCON_PORT || 25575);
const RCON_PASSWORD_FILE = process.env.RCON_PASSWORD_FILE || '';
const USERS_FILE = path.join(STATE_DIR, 'users.json');
const AUDIT_FILE = path.join(STATE_DIR, 'audit.jsonl');
const PENDING_FILE = path.join(STATE_DIR, 'pending-settings.json');
const PUSH_KEYS_FILE = path.join(STATE_DIR, 'push-vapid.json');
const PUSH_SUBSCRIPTIONS_FILE = path.join(STATE_DIR, 'push-subscriptions.json');
const COOKIE = ORIGIN.startsWith('https://') ? '__Host-dc_session' : 'dc_session';
const ITEM_CATALOG = require('./items.json');
const ITEM_IDS = new Set(ITEM_CATALOG.items);
const MOB_IDS = new Set(ITEM_CATALOG.items.filter((id) => /^minecraft:[a-z0-9_]+_spawn_egg$/.test(id))
  .map((id) => id.slice('minecraft:'.length, -'_spawn_egg'.length)));
const MOB_WORLDS = { world: 'minecraft:overworld', world_the_nether: 'minecraft:the_nether', world_the_end: 'minecraft:the_end' };
const sessions = new Map();
const failures = new Map();
const eventCooldowns = new Map();
const skinCatalogCache = new Map();
const pushTestCooldowns = new Map();
let pushSetupPromise;

class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

fs.mkdirSync(STATE_DIR, { recursive: true, mode: 0o700 });
fs.mkdirSync(JOBS_DIR, { recursive: true, mode: 0o700 });
fs.mkdirSync(CUSTOM_ITEMS_DIR, { recursive: true, mode: 0o700 });
fs.mkdirSync(TOWER_SCHEMATICS_DIR, { recursive: true, mode: 0o700 });

const GAME_RULES = ['keep_inventory', 'advance_time', 'players_sleeping_percentage', 'mob_griefing', 'spawn_mobs'];
const SETTINGS = {
  motd: { property: 'motd', valid: (v) => typeof v === 'string' && v.length >= 1 && v.length <= 80 && !/[\r\n\0]/.test(v) },
  maxPlayers: { property: 'max-players', valid: (v) => Number.isInteger(v) && v >= 2 && v <= 50 },
  pvp: { property: 'pvp', valid: (v) => typeof v === 'boolean' },
  viewDistance: { property: 'view-distance', valid: (v) => Number.isInteger(v) && v >= 4 && v <= 16 },
  simulationDistance: { property: 'simulation-distance', valid: (v) => Number.isInteger(v) && v >= 4 && v <= 12 },
};

function canonicalUser(value) {
  return Object.keys(readUsers()).find((name) => name.toLowerCase() === String(value || '').toLowerCase());
}

function readUsers() {
  try { return JSON.parse(fs.readFileSync(USERS_FILE, 'utf8')); }
  catch (error) {
    if (error.code === 'ENOENT') return {};
    throw error;
  }
}

function writeUsers(users) {
  const temp = `${USERS_FILE}.${process.pid}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(users, null, 2), { mode: 0o600 });
  fs.renameSync(temp, USERS_FILE);
}

function passwordRecord(password) {
  if (typeof password !== 'string' || password.length < 12 || password.length > 128) {
    throw new HttpError(400, 'Le mot de passe doit contenir entre 12 et 128 caractères.');
  }
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 128 * 1024 * 1024 });
  return { salt: salt.toString('hex'), hash: hash.toString('hex') };
}

function checkPassword(password, user) {
  if (!user?.salt || !user?.hash || typeof password !== 'string') return false;
  try {
    const actual = crypto.scryptSync(password, Buffer.from(user.salt, 'hex'), 64,
      { N: 32768, r: 8, p: 1, maxmem: 128 * 1024 * 1024 });
    return crypto.timingSafeEqual(actual, Buffer.from(user.hash, 'hex'));
  } catch { return false; }
}

async function passwordRecordAsync(password) {
  if (typeof password !== 'string' || password.length < 12 || password.length > 128)
    throw new HttpError(400, 'Le mot de passe doit contenir entre 12 et 128 caractères.');
  const salt = crypto.randomBytes(16);
  const hash = await scrypt(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 128 * 1024 * 1024 });
  return { salt: salt.toString('hex'), hash: hash.toString('hex') };
}

async function checkPasswordAsync(password, user) {
  if (!user?.salt || !user?.hash || typeof password !== 'string' || password.length > 128) return false;
  try {
    const actual = await scrypt(password, Buffer.from(user.salt, 'hex'), 64, { N: 32768, r: 8, p: 1, maxmem: 128 * 1024 * 1024 });
    return crypto.timingSafeEqual(actual, Buffer.from(user.hash, 'hex'));
  } catch { return false; }
}

function audit(event, username, detail = '') {
  fs.appendFileSync(AUDIT_FILE, `${JSON.stringify({ at: new Date().toISOString(), event, username, detail })}\n`,
    { mode: 0o600 });
}

function customItems() {
  return fs.readdirSync(CUSTOM_ITEMS_DIR).filter((name) => /^dc_[a-z][a-z0-9_]{2,31}\.yml$/.test(name))
    .map((file) => {
      const content = fs.readFileSync(path.join(CUSTOM_ITEMS_DIR, file), 'utf8');
      const name = content.match(/^display-name: (.+)$/m);
      let label = file.slice(0, -4);
      try { if (name) label = JSON.parse(name[1]); } catch {}
      return { id: file.slice(0, -4), name: label };
    }).sort((a, b) => a.name.localeCompare(b.name, 'fr'));
}

if (process.argv[2] === 'invite') {
  const requested = String(process.argv[3] || '');
  if (!/^[A-Za-z0-9_]{3,16}$/.test(requested)) throw new Error('Pseudo Minecraft invalide');
  const username = canonicalUser(requested) || requested;
  const token = crypto.randomBytes(32).toString('hex');
  const users = readUsers();
  users[username] = {
    ...(users[username] || {}),
    inviteHash: crypto.createHash('sha256').update(token).digest('hex'),
    inviteExpires: Date.now() + 48 * 60 * 60 * 1000,
  };
  writeUsers(users);
  audit('invite_created', username);
  process.stdout.write(`${ORIGIN}/activate#token=${token}\n`);
  process.exit(0);
}

if (process.argv[2] === 'set-password') {
  const username = canonicalUser(process.argv[3]);
  if (!username) throw new Error('Compte inconnu');
  const password = fs.readFileSync(0, 'utf8').replace(/\r?\n$/, '');
  const users = readUsers();
  users[username] = { ...passwordRecord(password) };
  writeUsers(users);
  audit('password_set_cli', username);
  process.stdout.write('OK\n');
  process.exit(0);
}

function securityHeaders(res) {
  res.setHeader('X-Robots-Tag', 'noindex, nofollow, noarchive, nosnippet');
  res.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  res.setHeader('Cache-Control', 'no-store');
  if (ORIGIN.startsWith('https://')) res.setHeader('Strict-Transport-Security', 'max-age=31536000');
}

function json(res, status, value) {
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(value));
}

async function body(req, maxBytes = 8192) {
  let value = '';
  for await (const chunk of req) {
    value += chunk;
    if (value.length > maxBytes) throw new HttpError(413, 'Requête trop volumineuse.');
  }
  try { return JSON.parse(value || '{}'); }
  catch { throw new HttpError(400, 'JSON invalide.'); }
}

function requireOrigin(req) {
  if (req.headers.origin !== ORIGIN) throw new HttpError(403, 'Origine refusée.');
}

function clientIp(req) {
  return String(req.headers['x-forwarded-for'] || req.socket.remoteAddress).split(',')[0].trim();
}

function sessionOf(req) {
  const raw = String(req.headers.cookie || '').split(';').map((item) => item.trim());
  const cookie = raw.find((item) => item.startsWith(`${COOKIE}=`));
  const id = cookie?.slice(COOKIE.length + 1);
  const session = sessions.get(id);
  if (!session) return null;
  if (session.expires < Date.now()) { sessions.delete(id); return null; }
  return { id, ...session };
}

function requireSession(req) {
  const session = sessionOf(req);
  if (!session) throw new HttpError(401, 'Connexion requise.');
  return session;
}

function requireCsrf(req, session) {
  requireOrigin(req);
  if (req.headers['x-csrf-token'] !== session.csrf) throw new HttpError(403, 'Jeton de session invalide.');
}

function cookieValue(id, maxAge = 28800) {
  return `${COOKIE}=${id}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${maxAge}${ORIGIN.startsWith('https://') ? '; Secure' : ''}`;
}

function throttle(key, failed = false) {
  const now = Date.now();
  const item = failures.get(key) || { count: 0, until: now + 15 * 60 * 1000 };
  if (item.until < now) { item.count = 0; item.until = now + 15 * 60 * 1000; }
  if (item.count >= 6) throw new HttpError(429, 'Trop de tentatives. Réessaie dans 15 minutes.');
  if (failed) { item.count += 1; failures.set(key, item); }
  return item;
}

function packet(id, type, value) {
  const payload = Buffer.from(value, 'utf8');
  const buffer = Buffer.alloc(14 + payload.length);
  buffer.writeInt32LE(10 + payload.length, 0);
  buffer.writeInt32LE(id, 4);
  buffer.writeInt32LE(type, 8);
  payload.copy(buffer, 12);
  return buffer;
}

function rconBatch(commands, timeoutMs = 12000) {
  const password = fs.readFileSync(RCON_PASSWORD_FILE, 'utf8').trim();
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: RCON_HOST, port: RCON_PORT });
    socket.setNoDelay(true);
    let received = Buffer.alloc(0);
    let authed = false;
    let finished = false;
    let idle;
    let currentIndex = 0;
    const timer = setTimeout(() => finish(new Error('RCON timeout')), timeoutMs);
    const answers = commands.map(() => '');
    const answered = new Set();
    function finish(error) {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      clearTimeout(idle);
      socket.destroy();
      if (error) reject(error);
      else resolve(answers.map((answer) => answer.replace(/\u00a7[0-9a-fk-orx]/gi, '').trim()));
    }
    socket.on('connect', () => socket.write(packet(101, 3, password)));
    socket.on('error', finish);
    socket.on('close', () => { if (!finished) finish(new Error(`RCON disconnected (${answered.size}/${commands.length})`)); });
    socket.on('data', (chunk) => {
      received = Buffer.concat([received, chunk]);
      if (received.length > 512 * 1024) return finish(new Error('RCON response too large'));
      while (received.length >= 4) {
        const size = received.readInt32LE(0);
        if (size < 10 || size > 512 * 1024) return finish(new Error('Invalid RCON packet'));
        if (received.length < size + 4) break;
        const id = received.readInt32LE(4);
        const type = received.readInt32LE(8);
        const text = received.subarray(12, size + 2).toString('utf8');
        if (process.env.RCON_DEBUG === '1') console.error('RCON packet', id, type, text.slice(0, 100));
        received = received.subarray(size + 4);
        if (id === -1) return finish(new Error('RCON authentication failed'));
        if (!authed && id === 101 && type === 2) {
          authed = true;
          socket.write(packet(102, 2, commands[0]));
        } else if (authed && id >= 102 && id < 102 + commands.length && type === 0) {
          const index = id - 102;
          answers[index] += text;
          answered.add(index);
          if (index === currentIndex && currentIndex + 1 < commands.length) {
            clearTimeout(idle);
            currentIndex += 1;
            idle = setTimeout(() => socket.write(packet(102 + currentIndex, 2, commands[currentIndex])), 100);
          } else if (answered.size === commands.length) {
            clearTimeout(idle);
            idle = setTimeout(() => finish(), 250);
          }
        }
      }
    });
  });
}

async function rcon(command, timeoutMs) { return (await rconBatch([command], timeoutMs))[0]; }

function panelReply(raw) {
  const marker = raw.indexOf('DCPLAYER:');
  if (marker < 0) throw new HttpError(409, 'La fiche détaillée sera disponible après le chargement de DuckCatWelcome.');
  let value;
  try { value = JSON.parse(raw.slice(marker + 'DCPLAYER:'.length).trim()); }
  catch { throw new HttpError(502, 'Réponse du jeu illisible. Réessaie.'); }
  const errors = {
    offline: [409, 'Ce joueur n’est plus connecté.'],
    changed: [409, 'Son inventaire a changé pendant que tu regardais. Actualise avant de modifier.'],
    range: [400, 'Quantité hors limites pour cet objet.'],
    invalid: [400, 'Objet ou emplacement invalide.'],
    syntax: [400, 'Action invalide.'],
  };
  if (value.error) {
    const [status, message] = errors[value.error] || [502, 'Le jeu a refusé cette action.'];
    throw new HttpError(status, message);
  }
  return value;
}

function panelSlot(value) {
  return /^(?:main:(?:[0-9]|[12][0-9]|3[0-5])|ender:(?:[0-9]|1[0-9]|2[0-6])|equipment:(?:head|chest|legs|feet|offhand))$/.test(value);
}

function panelRevision(value) { return /^(?:0|[a-f0-9]{1,8})$/.test(value); }

async function skinSource(url, maxBytes = 512 * 1024) {
  let response;
  try {
    response = await fetch(url, { headers: { 'User-Agent': 'DuckCatMineAdmin/1.0 (skin gallery)' }, signal: AbortSignal.timeout(8000) });
  } catch { throw new HttpError(502, 'La galerie de skins ne répond pas. Réessaie dans un instant.'); }
  if (!response.ok) throw new HttpError(response.status === 404 ? 404 : 502, 'Skin introuvable chez le fournisseur.');
  const chunks = [];
  let total = 0;
  for await (const chunk of response.body) {
    total += chunk.length;
    if (total > maxBytes) throw new HttpError(502, 'Réponse du fournisseur trop volumineuse.');
    chunks.push(chunk);
  }
  return { response, data: Buffer.concat(chunks) };
}

async function skinCatalog(query) {
  const key = query ? `search:${query}` : 'trending';
  const cached = skinCatalogCache.get(key);
  if (cached && cached.until > Date.now()) return cached.value;
  let value;
  if (query) {
    const raw = JSON.parse((await skinSource(`https://api.mineskin.org/v2/skins/?size=24&filter=${encodeURIComponent(query)}`)).data.toString('utf8'));
    if (!raw.success || !Array.isArray(raw.skins)) throw new HttpError(502, 'Réponse de la recherche de skins invalide.');
    value = {
      source: 'https://mineskin.org',
      skins: raw.skins.filter((skin) => /^[a-f0-9]{32}$/.test(skin.uuid || '') && /^[a-f0-9]{64}$/.test(skin.texture || ''))
        .map((skin) => ({ id: skin.uuid, hash: skin.texture, name: String(skin.name || '').slice(0, 50), source: 'mineskin' })),
    };
  } else {
    const raw = JSON.parse((await skinSource('https://craftdex.net/skins/trending.json')).data.toString('utf8'));
    if (!Array.isArray(raw)) throw new HttpError(502, 'Réponse de la galerie invalide.');
    value = {
      source: 'https://craftdex.net',
      skins: raw.filter((skin) => !skin.banned && /^[0-9a-f-]{36}$/i.test(skin.id || '') && /^[0-9a-f]{64}$/i.test(skin.hash || ''))
        .slice(0, 36).map((skin) => ({ id: skin.id, hash: skin.hash, slim: !!skin.slim, source: 'craftdex',
          creator: /^[A-Za-z0-9_]{3,16}$/.test(skin.first_player?.username || '') ? skin.first_player.username : '',
          players: Number(skin.players_count) || 0 })),
    };
  }
  if (skinCatalogCache.size > 40) skinCatalogCache.clear();
  skinCatalogCache.set(key, { until: Date.now() + 5 * 60 * 1000, value });
  return value;
}

function readJsonFile(filename, fallback) {
  try { return JSON.parse(fs.readFileSync(path.join(MC_DATA_DIR, filename), 'utf8')); }
  catch { return fallback; }
}

const logReader = new LogReader(path.join(MC_DATA_DIR, 'logs', 'latest.log'));
async function recentLog() { return (await logReader.read()).logs; }
async function recentChat() { return (await logReader.read()).messages; }

function mapStart() {
  if (/^#[a-z0-9_:-]+(?:\.[0-9]+)?[a-z0-9_:.-]*$/i.test(process.env.MAP_START || '')) return process.env.MAP_START;
  try {
    const config = fs.readFileSync(path.join(MC_DATA_DIR, 'plugins/DuckCatWelcome/config.yml'), 'utf8');
    const spawn = config.match(/^spawn:\s*\n((?:[ \t]+.*\n)+)/m)?.[1] || '';
    const value = key => spawn.match(new RegExp(`^\\s+${key}:\\s*([^\\s]+)`, 'm'))?.[1];
    const world = value('world'), xyz = ['x', 'y', 'z'].map(key => Number(value(key)));
    if (/^[a-z0-9_]+$/i.test(world || '') && xyz.every(Number.isFinite)) return `#${world}:${xyz.join(':')}:350:0:0:0:0:flat`;
  } catch {}
  return '#world:0:80:0:350:0:0:0:0:flat';
}

function mapPolicy() {
  try {
    const content = fs.readFileSync(path.join(MC_DATA_DIR, 'plugins/BlueMap/plugin.conf'), 'utf8');
    return content.match(/^player-render-limit:\s*1\s*$/m) ? 'idle' : 'live';
  } catch { return 'live'; }
}

const metadata = new SharedSnapshot(async () => {
  const queries = ['worldborder get', 'banlist players', 'plugins', ...GAME_RULES.map(name => `gamerule ${name}`)];
  const answers = await rconBatch(queries);
  const gamerules = {};
  GAME_RULES.forEach((name, index) => {
    const match = (answers[index + 3] || '').match(/currently set to (true|false|\d+)/i);
    if (match) gamerules[name] = match[1];
  });
  return { worldborder: answers[0], banlist: answers[1], plugins: answers[2], gamerules };
}, 60000);
const statusSnapshot = new SharedSnapshot(async () => {
  let answers = [], meta = metadata.value || {};
  try { answers = await rconBatch(['list', 'tps', 'mspt']); meta = await metadata.get(); } catch {}
  return {
    online: answers.length > 0, players: answers[0] || '', tps: answers[1] || '', mspt: answers[2] || '', ...meta,
    ops: readJsonFile('ops.json', []), whitelist: readJsonFile('whitelist.json', []),
    backups: backups(), properties: properties(), logs: await recentLog(), metrics: systemMetrics(),
    pending: pendingSettings(), scheduledRestart: scheduledRestart(), observedAt: new Date().toISOString(),
    address: process.env.MC_ADDRESS || new URL(ORIGIN).hostname, mapStart: mapStart(), mapPolicy: mapPolicy(),
  };
});
const eventClients = new Set();
function sendEvent(client, name, data) {
  if (client.res.destroyed || client.res.writableLength > 256 * 1024) { client.res.end(); eventClients.delete(client); return; }
  client.res.write(`event: ${name}\ndata: ${JSON.stringify(data)}\n\n`);
}
function broadcast(name, data) { for (const client of eventClients) sendEvent(client, name, data); }

function readPushSubscriptions() {
  try {
    const entries = JSON.parse(fs.readFileSync(PUSH_SUBSCRIPTIONS_FILE, 'utf8'));
    return Array.isArray(entries) ? entries : [];
  } catch (error) {
    if (error.code === 'ENOENT') return [];
    throw error;
  }
}

function writePushSubscriptions(entries) {
  const temp = `${PUSH_SUBSCRIPTIONS_FILE}.${process.pid}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(entries), { mode: 0o600 });
  fs.renameSync(temp, PUSH_SUBSCRIPTIONS_FILE);
}

async function pushClient() {
  if (!pushSetupPromise) pushSetupPromise = (async () => {
    const webPush = (await import('web-push')).default;
    let keys;
    try { keys = JSON.parse(fs.readFileSync(PUSH_KEYS_FILE, 'utf8')); }
    catch (error) {
      if (error.code !== 'ENOENT') throw error;
      keys = webPush.generateVAPIDKeys();
      try { fs.writeFileSync(PUSH_KEYS_FILE, JSON.stringify(keys), { flag: 'wx', mode: 0o600 }); }
      catch (writeError) {
        if (writeError.code !== 'EEXIST') throw writeError;
        keys = JSON.parse(fs.readFileSync(PUSH_KEYS_FILE, 'utf8'));
      }
    }
    webPush.setVapidDetails(process.env.VAPID_SUBJECT || (ORIGIN.startsWith('https://') ? ORIGIN : 'https://github.com/drslid/DuckCatMine'), keys.publicKey, keys.privateKey);
    return { webPush, publicKey: keys.publicKey };
  })();
  return pushSetupPromise;
}

function validPushSubscription(value) {
  if (!value || typeof value.endpoint !== 'string' || value.endpoint.length > 2048
      || !value.keys || typeof value.keys !== 'object') return false;
  let endpoint;
  try { endpoint = new URL(value.endpoint); } catch { return false; }
  return endpoint.protocol === 'https:' && !endpoint.username && !endpoint.password && (!endpoint.port || endpoint.port === '443')
    && (endpoint.hostname === 'fcm.googleapis.com' || endpoint.hostname === 'updates.push.services.mozilla.com'
      || endpoint.hostname.endsWith('.push.apple.com'))
    && /^[A-Za-z0-9_-]{40,200}$/.test(value.keys.p256dh || '')
    && /^[A-Za-z0-9_-]{16,80}$/.test(value.keys.auth || '');
}

async function sendPush(entry, payload) {
  const { webPush } = await pushClient();
  try {
    await webPush.sendNotification({ endpoint: entry.endpoint, keys: entry.keys }, JSON.stringify(payload), { TTL: 300 });
  } catch (error) {
    if (![404, 410].includes(error.statusCode)) throw error;
    writePushSubscriptions(readPushSubscriptions().filter((item) => item.endpoint !== entry.endpoint));
    return false;
  }
  return true;
}

let lastPushChatId = '';
let pushMonitorBusy = false;
async function monitorPushChat() {
  if (pushMonitorBusy) return;
  pushMonitorBusy = true;
  try {
    const messages = await recentChat();
    const latest = messages.at(-1);
    if (!latest || latest.id === lastPushChatId) return;
    if (!lastPushChatId) { lastPushChatId = latest.id; return; }
    const previous = lastPushChatId.match(/^(\d+):(\d+)$/);
    const unseen = messages.filter((entry) => {
      const cursor = entry.id.match(/^(\d+):(\d+)$/);
      return !previous || cursor[1] !== previous[1] || Number(cursor[2]) > Number(previous[2]);
    }).slice(-20);
    lastPushChatId = latest.id;
    const subscriptions = readPushSubscriptions();
    for (const message of unseen.filter((entry) => entry.author !== 'Serveur')) {
      await Promise.allSettled(subscriptions.filter((entry) => entry.username !== message.author)
        .map((entry) => sendPush(entry, {
          title: 'Chat · DuckCatMine', body: `${message.author} : ${message.message}`.slice(0, 180),
          id: message.id, url: '/#chat',
        })));
    }
  } catch (error) { console.error('Notifications chat :', error); }
  finally { pushMonitorBusy = false; }
}

function backups() {
  try {
    return fs.readdirSync(BACKUP_DIR)
      .filter((name) => /^(world|manual|pre-restore)-[0-9-]+\.tar\.gz$/.test(name))
      .map((name) => {
        const stat = fs.statSync(path.join(BACKUP_DIR, name));
        return { name, size: stat.size, mtimeMs: stat.mtimeMs };
      })
      .sort((a, b) => b.mtimeMs - a.mtimeMs)
      .slice(0, 20)
      .map(({ name, size, mtimeMs }) => ({ name, size, at: new Date(mtimeMs).toISOString() }));
  } catch { return []; }
}

function pendingSettings() {
  try { return JSON.parse(fs.readFileSync(PENDING_FILE, 'utf8')); }
  catch (error) { if (error.code === 'ENOENT') return {}; throw error; }
}

function writePending(settings) {
  const temp = `${PENDING_FILE}.${process.pid}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(settings, null, 2), { mode: 0o600 });
  fs.renameSync(temp, PENDING_FILE);
}

function enqueueJob(type, username, extra = {}) {
  const id = crypto.randomBytes(16).toString('hex');
  const filename = path.join(JOBS_DIR, `${id}.request`);
  const temp = `${filename}.${process.pid}.tmp`;
  fs.writeFileSync(temp, JSON.stringify({ id, type, username, at: new Date().toISOString(), ...extra }), { mode: 0o600 });
  fs.renameSync(temp, filename);
  audit('job_queued', username, `${type}:${id}`);
  return id;
}

function jobStatus(id) {
  if (!/^[a-f0-9]{32}$/.test(id)) throw new HttpError(400, 'Identifiant invalide.');
  for (const suffix of ['done', 'running', 'request']) {
    try {
      const filename = path.join(JOBS_DIR, `${id}.${suffix}`);
      const raw = fs.readFileSync(filename, 'utf8');
      if (suffix === 'done') return { status: 'done', ...JSON.parse(raw) };
      return { status: suffix === 'request' ? 'queued' : 'running' };
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  throw new HttpError(404, 'Opération introuvable.');
}

function scheduledRestart() {
  for (const name of fs.readdirSync(JOBS_DIR)) {
    if (!/^[a-f0-9]{32}\.(request|running)$/.test(name)) continue;
    try {
      const job = JSON.parse(fs.readFileSync(path.join(JOBS_DIR, name), 'utf8'));
      if (job.type === 'restart') return { id: job.id, status: name.endsWith('.running') ? 'running' : 'queued' };
    } catch {}
  }
  return null;
}

function systemMetrics() {
  const result = {};
  try {
    const stat = fs.statfsSync(MC_DATA_DIR);
    result.diskTotal = stat.blocks * stat.bsize;
    result.diskFree = stat.bavail * stat.bsize;
  } catch {}
  try { Object.assign(result, JSON.parse(fs.readFileSync(path.join(JOBS_DIR, 'metrics.json'), 'utf8'))); }
  catch {}
  return result;
}

function backupFile(name) {
  if (!/^(world|manual|pre-restore)-[0-9-]+\.tar\.gz$/.test(name)) throw new HttpError(400, 'Nom de sauvegarde invalide.');
  const filename = path.join(BACKUP_DIR, name);
  let stat;
  try { stat = fs.statSync(filename); }
  catch (error) { if (error.code === 'ENOENT') throw new HttpError(404, 'Sauvegarde introuvable.'); throw error; }
  if (!stat.isFile()) throw new HttpError(404, 'Sauvegarde introuvable.');
  return { filename, stat };
}

if (process.argv[2] === 'rcon-test') {
  rcon('list').then((result) => { process.stdout.write(`${result}\n`); process.exit(0); })
    .catch((error) => { console.error(error); process.exit(1); });
  return;
}

if (process.argv[2] === 'batch-test') {
  rconBatch(process.argv.slice(3).length ? process.argv.slice(3) : ['list', 'tps', 'mspt', 'worldborder get', 'banlist players', 'plugins', ...GAME_RULES.map((name) => `gamerule ${name}`)]).then((results) => {
    process.stdout.write(`${JSON.stringify(results)}\n`);
    process.exit(0);
  }).catch((error) => { console.error(error); process.exit(1); });
  return;
}

function properties() {
  try {
    const wanted = new Set(['gamemode', 'difficulty', 'max-players', 'online-mode', 'white-list', 'enforce-whitelist', 'pvp', 'view-distance', 'simulation-distance', 'motd']);
    return Object.fromEntries(fs.readFileSync(path.join(MC_DATA_DIR, 'server.properties'), 'utf8')
      .split('\n').map((line) => line.trim()).filter((line) => line && !line.startsWith('#'))
      .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)])
      .filter(([key]) => wanted.has(key)));
  } catch { return {}; }
}

async function serve(req, res) {
  securityHeaders(res);
  const url = new URL(req.url, ORIGIN);
  if (req.method === 'POST' && /^\/api\/(?:command|settings|jobs|map\/policy)$/.test(url.pathname)) {
    res.once('finish', () => { if (res.statusCode < 400) {
      if (url.pathname === '/api/command') metadata.invalidate();
      statusSnapshot.invalidate();
    } });
  }
  if (req.method === 'GET' && url.pathname === '/healthz') {
    return json(res, 200, { ok: true });
  }
  if (req.method === 'GET' && ['/', '/activate', '/map-picker.js'].includes(url.pathname)) {
    const file = url.pathname === '/map-picker.js' ? 'map-picker.js' : 'v2/index.html';
    const filename = path.join(__dirname, 'public', file);
    if (!fs.existsSync(filename)) return json(res, 503, {error:'Interface not built. Run npm ci && npm run build in admin/client.'});
    res.setHeader('Content-Type', file.endsWith('.js') ? 'application/javascript; charset=utf-8' : 'text/html; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    return fs.createReadStream(filename).pipe(res);
  }
  if (req.method === 'GET' && /^\/assets\/[a-zA-Z0-9_.-]+\.(?:js|css)$/.test(url.pathname)) {
    const filename = path.join(__dirname, 'public/v2/assets', path.basename(url.pathname));
    if (!fs.existsSync(filename)) throw new HttpError(404, 'Ressource introuvable.');
    res.setHeader('Content-Type', filename.endsWith('.js') ? 'application/javascript; charset=utf-8' : 'text/css; charset=utf-8');
    res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
    return fs.createReadStream(filename).pipe(res);
  }
  const publicAssets = {
    '/logo.svg': ['logo.svg', 'image/svg+xml'],
    '/favicon-32.png': ['favicon-32.png', 'image/png'],
    '/apple-touch-icon.png': ['apple-touch-icon.png', 'image/png'],
    '/app-icon-192.png': ['app-icon-192.png', 'image/png'],
    '/app-icon-512.png': ['app-icon-512.png', 'image/png'],
    '/manifest.webmanifest': ['manifest.webmanifest', 'application/manifest+json'],
    '/sw.js': ['sw.js', 'application/javascript; charset=utf-8'],
  };
  if (req.method === 'GET' && publicAssets[url.pathname]) {
    const [file, type] = publicAssets[url.pathname];
    res.setHeader('Content-Type', type);
    if (file === 'sw.js') res.setHeader('Service-Worker-Allowed', '/');
    return fs.createReadStream(path.join(__dirname, 'public', file)).pipe(res);
  }
  if (req.method === 'GET' && /^\/icons\/[a-z0-9_]+\.png$/.test(url.pathname)) {
    const filename = path.join(__dirname, 'public', 'icons', path.basename(url.pathname));
    if (!fs.existsSync(filename)) throw new HttpError(404, 'Image introuvable.');
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'public, max-age=604800');
    return fs.createReadStream(filename).pipe(res);
  }
  if (req.method === 'POST' && url.pathname === '/api/invite-info') {
    requireOrigin(req);
    throttle(`invite:${clientIp(req)}`);
    const { token } = await body(req);
    const hash = crypto.createHash('sha256').update(String(token || '')).digest('hex');
    const user = Object.entries(readUsers()).find(([, value]) => value.inviteHash === hash && value.inviteExpires > Date.now());
    if (!user) { throttle(`invite:${clientIp(req)}`, true); throw new HttpError(400, 'Lien invalide ou expiré.'); }
    return json(res, 200, { username: user[0] });
  }
  if (req.method === 'POST' && url.pathname === '/api/activate') {
    requireOrigin(req);
    throttle(`activate:${clientIp(req)}`);
    const { token, password } = await body(req);
    const hash = crypto.createHash('sha256').update(String(token || '')).digest('hex');
    const users = readUsers();
    const username = Object.keys(users).find((key) => users[key].inviteHash === hash && users[key].inviteExpires > Date.now());
    if (!username) { throttle(`activate:${clientIp(req)}`, true); throw new HttpError(400, 'Lien invalide ou expiré.'); }
    users[username] = await passwordRecordAsync(password);
    writeUsers(users);
    audit('account_activated', username);
    return json(res, 200, { ok: true, username });
  }
  if (req.method === 'POST' && url.pathname === '/api/login') {
    requireOrigin(req);
    const input = await body(req);
    const username = canonicalUser(input.username);
    const key = `login:${clientIp(req)}:${username || 'unknown'}`;
    throttle(key);
    if (!username || !(await checkPasswordAsync(input.password, readUsers()[username]))) {
      throttle(key, true);
      audit('login_failed', username || 'unknown');
      throw new HttpError(401, 'Pseudo ou mot de passe incorrect.');
    }
    failures.delete(key);
    const id = crypto.randomBytes(32).toString('hex');
    const csrf = crypto.randomBytes(24).toString('hex');
    sessions.set(id, { username, csrf, expires: Date.now() + 8 * 60 * 60 * 1000 });
    res.setHeader('Set-Cookie', cookieValue(id));
    audit('login', username);
    return json(res, 200, { username, csrf });
  }
  if (req.method === 'GET' && url.pathname === '/api/me') {
    const session = requireSession(req);
    return json(res, 200, { username: session.username, csrf: session.csrf });
  }
  if (req.method === 'POST' && url.pathname === '/api/logout') {
    const session = requireSession(req);
    requireCsrf(req, session);
    sessions.delete(session.id);
    res.setHeader('Set-Cookie', cookieValue('', 0));
    audit('logout', session.username);
    return json(res, 200, { ok: true });
  }
  if (req.method === 'POST' && url.pathname === '/api/password') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const users = readUsers();
    if (!(await checkPasswordAsync(input.currentPassword, users[session.username]))) throw new HttpError(403, 'Mot de passe actuel incorrect.');
    users[session.username] = await passwordRecordAsync(input.newPassword);
    writeUsers(users);
    sessions.delete(session.id);
    res.setHeader('Set-Cookie', cookieValue('', 0));
    audit('password_changed', session.username);
    return json(res, 200, { ok: true });
  }
  if (req.method === 'GET' && url.pathname === '/api/status') {
    requireSession(req);
    return json(res, 200, await statusSnapshot.get());
  }
  if (req.method === 'GET' && url.pathname === '/api/events') {
    const session = requireSession(req);
    if (eventClients.size >= 64 || [...eventClients].filter(c => c.session.id === session.id).length >= 4)
      throw new HttpError(429, 'Trop de connexions ouvertes.');
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive', 'X-Accel-Buffering': 'no' });
    res.flushHeaders();
    const client = { res, session };
    eventClients.add(client);
    res.on('close', () => eventClients.delete(client));
    sendEvent(client, 'chat', { messages: await recentChat() });
    sendEvent(client, 'status', await statusSnapshot.get());
    return;
  }
  if (req.method === 'GET' && url.pathname === '/api/chat') {
    requireSession(req);
    return json(res, 200, { messages: await recentChat() });
  }
  if (req.method === 'POST' && url.pathname === '/api/chat/send') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const { message: raw } = await body(req);
    const message = String(raw || '').trim();
    if (!message || message.length > 180 || /[\r\n\0]/.test(message)) throw new HttpError(400, 'Message invalide (180 caractères maximum).');
    const result = await rcon(`say [Web:${session.username}] ${message}`);
    if (/unknown or incomplete command|unknown command|error/i.test(result)) throw new HttpError(409, result.slice(0, 300));
    audit('chat_web', session.username, `${message.length} caractères`);
    return json(res, 200, { ok: true });
  }
  if (req.method === 'GET' && url.pathname === '/api/push/config') {
    const session = requireSession(req);
    const { publicKey } = await pushClient();
    audit('push_config', session.username);
    return json(res, 200, { publicKey });
  }
  if (req.method === 'POST' && url.pathname === '/api/push/subscribe') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const { subscription } = await body(req);
    if (!validPushSubscription(subscription)) throw new HttpError(400, 'Abonnement aux notifications invalide.');
    await pushClient();
    const entries = readPushSubscriptions().filter((entry) => entry.endpoint !== subscription.endpoint);
    entries.push({ username: session.username, endpoint: subscription.endpoint,
      keys: { p256dh: subscription.keys.p256dh, auth: subscription.keys.auth }, at: new Date().toISOString() });
    const own = entries.filter((entry) => entry.username === session.username);
    if (own.length > 8) entries.splice(entries.findIndex((entry) => entry.endpoint === own[0].endpoint), 1);
    writePushSubscriptions(entries);
    audit('push_subscribed', session.username);
    return json(res, 200, { ok: true });
  }
  if (req.method === 'POST' && url.pathname === '/api/push/unsubscribe') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const { endpoint } = await body(req);
    if (typeof endpoint !== 'string' || endpoint.length > 2048) throw new HttpError(400, 'Abonnement invalide.');
    writePushSubscriptions(readPushSubscriptions().filter((entry) => entry.username !== session.username || entry.endpoint !== endpoint));
    audit('push_unsubscribed', session.username);
    return json(res, 200, { ok: true });
  }
  if (req.method === 'POST' && url.pathname === '/api/push/test') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const { endpoint } = await body(req);
    const entry = readPushSubscriptions().find((item) => item.username === session.username && item.endpoint === endpoint);
    if (!entry) throw new HttpError(404, 'Active d’abord les notifications sur ce téléphone.');
    if (Date.now() - (pushTestCooldowns.get(session.username) || 0) < 15000) throw new HttpError(429, 'Attends 15 secondes avant un autre test.');
    pushTestCooldowns.set(session.username, Date.now());
    let delivered;
    try {
      delivered = await sendPush(entry, { title: 'DuckCatMine', body: 'Les notifications du chat fonctionnent sur ce téléphone.',
        id: `test:${Date.now()}`, url: '/#chat' });
    } catch (error) {
      console.error('Test notification refusé :', error.statusCode || error.code || 'erreur inconnue');
      throw new HttpError(502, `Le service de notifications a refusé l’envoi (code ${error.statusCode || 'réseau'}).`);
    }
    if (!delivered) throw new HttpError(410, 'L’abonnement a expiré. Désactive puis réactive les notifications sur ce téléphone.');
    audit('push_test', session.username);
    return json(res, 200, { ok: true });
  }
  if (req.method === 'GET' && /^\/api\/players\/[A-Za-z0-9_]{3,16}\/details$/.test(url.pathname)) {
    requireSession(req);
    const player = url.pathname.split('/')[3];
    const status = panelReply(await rcon(`dcpanel status ${player}`));
    const section = url.searchParams.get('section') || 'all';
    if (!['all', 'none', 'main', 'equipment', 'ender'].includes(section)) throw new HttpError(400, 'Section invalide.');
    const queries = [
      ...(['all', 'main'].includes(section) ? [0, 1, 2, 3].map(page => `dcpanel inventory ${player} main ${page}`) : []),
      ...(['all', 'equipment'].includes(section) ? [`dcpanel inventory ${player} equipment 0`] : []),
      ...(['all', 'ender'].includes(section) ? [0, 1, 2].map(page => `dcpanel inventory ${player} ender ${page}`) : []),
    ];
    const pages = queries.length ? (await rconBatch(queries, 20000)).map(panelReply) : [];
    const inventory = { main: [], equipment: [], ender: [] };
    for (const page of pages) {
      if (!inventory[page.section] || !Array.isArray(page.items)) throw new HttpError(502, 'Inventaire illisible.');
      inventory[page.section].push(...page.items);
    }
    return json(res, 200, { status, inventory });
  }
  if (req.method === 'POST' && url.pathname === '/api/players/live-action') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const player = String(input.player || '').trim();
    const operation = String(input.operation || '');
    if (!/^[A-Za-z0-9_]{3,16}$/.test(player)) throw new HttpError(400, 'Joueur invalide.');
    let command;
    if (['heal', 'feed'].includes(operation)) command = `dcpanel ${operation} ${player}`;
    else if (operation === 'xp') {
      const levels = Number(input.levels);
      if (!Number.isInteger(levels) || levels < -1000 || levels > 1000) throw new HttpError(400, 'Niveaux invalides.');
      command = `dcpanel xp ${player} ${levels}`;
    } else if (['set', 'clear', 'amount', 'swap'].includes(operation)) {
      const slot = String(input.slot || '');
      const rev = String(input.rev || '');
      if (!panelSlot(slot) || !panelRevision(rev)) throw new HttpError(400, 'Emplacement invalide.');
      command = `dcpanel ${operation} ${player} ${slot} ${rev}`;
      if (operation === 'set') {
        const item = String(input.item || '');
        const count = Number(input.count);
        if (!ITEM_IDS.has(item) || !Number.isInteger(count) || count < 1 || count > 64) throw new HttpError(400, 'Choisis un objet valide et une quantité de 1 à 64.');
        command += ` ${item} ${count}`;
      } else if (operation === 'amount') {
        const count = Number(input.count);
        if (!Number.isInteger(count) || count < 1 || count > 64) throw new HttpError(400, 'Quantité invalide.');
        command += ` ${count}`;
      } else if (operation === 'swap') {
        const target = String(input.targetSlot || '');
        const targetRev = String(input.targetRev || '');
        if (!panelSlot(target) || !panelRevision(targetRev) || target === slot) throw new HttpError(400, 'Destination invalide.');
        command += ` ${target} ${targetRev}`;
      }
    } else throw new HttpError(400, 'Action invalide.');
    const result = panelReply(await rcon(command));
    audit('player_live_action', session.username, `${operation} ${player} ${String(input.slot || '')}`);
    return json(res, 200, result);
  }
  if (req.method === 'GET' && url.pathname === '/api/skins/catalog') {
    requireSession(req);
    const query = String(url.searchParams.get('query') ?? url.searchParams.get('tag') ?? '').toLowerCase().trim();
    if (query && !/^[a-z0-9 -]{2,32}$/.test(query)) throw new HttpError(400, 'Recherche invalide.');
    return json(res, 200, await skinCatalog(query));
  }
  if (req.method === 'GET' && /^\/api\/skins\/texture\/[a-f0-9]{64}$/.test(url.pathname)) {
    requireSession(req);
    const hash = url.pathname.slice('/api/skins/texture/'.length);
    const image = await skinSource(`https://textures.minecraft.net/texture/${hash}`, 64 * 1024);
    if (!image.data.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'))) throw new HttpError(502, 'Texture de skin invalide.');
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'private, max-age=86400');
    return res.end(image.data);
  }
  if (req.method === 'GET' && /^\/api\/skins\/player-image\/[A-Za-z0-9_]{3,16}$/.test(url.pathname)) {
    requireSession(req);
    const name = url.pathname.slice('/api/skins/player-image/'.length);
    const image = await skinSource(`https://api.mcskin.me/skin/${name}`, 64 * 1024);
    if (!image.data.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'))) throw new HttpError(502, 'Texture de skin invalide.');
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'private, max-age=300');
    return res.end(image.data);
  }
  if (req.method === 'GET' && url.pathname === '/api/items/catalog') {
    requireSession(req);
    return json(res, 200, ITEM_CATALOG);
  }
  if (req.method === 'GET' && url.pathname === '/api/custom-items') {
    requireSession(req);
    return json(res, 200, { items: customItems(), available: fs.existsSync(path.join(MC_DATA_DIR, 'plugins')) && fs.readdirSync(path.join(MC_DATA_DIR, 'plugins')).some(name => /^mcraft.*\.jar$/i.test(name)) });
  }
  if (req.method === 'GET' && url.pathname === '/api/map-auth') {
    requireSession(req);
    return json(res, 200, { ok: true });
  }
  if (req.method === 'POST' && url.pathname === '/api/map/policy') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const { policy } = await body(req);
    if (!['idle', 'live'].includes(policy)) throw new HttpError(400, 'Politique de carte invalide.');
    return json(res, 202, { id: enqueueJob('map-policy', session.username, { policy }) });
  }
  if (req.method === 'GET' && url.pathname.startsWith('/api/backups/')) {
    requireSession(req);
    const name = decodeURIComponent(url.pathname.slice('/api/backups/'.length));
    const { filename, stat } = backupFile(name);
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/gzip');
    res.setHeader('Content-Length', stat.size);
    res.setHeader('Content-Disposition', `attachment; filename="${name}"`);
    return fs.createReadStream(filename).pipe(res);
  }
  if (req.method === 'GET' && url.pathname.startsWith('/api/jobs/')) {
    requireSession(req);
    return json(res, 200, jobStatus(url.pathname.slice('/api/jobs/'.length)));
  }
  if (req.method === 'POST' && url.pathname === '/api/settings') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    if (!input || typeof input !== 'object' || Array.isArray(input)) throw new HttpError(400, 'Réglages invalides.');
    const settings = {};
    for (const [key, value] of Object.entries(input)) {
      if (!SETTINGS[key]?.valid(value)) throw new HttpError(400, `Réglage invalide : ${key}.`);
      settings[key] = value;
    }
    if (!Object.keys(settings).length) throw new HttpError(400, 'Aucun réglage.');
    const merged = { ...pendingSettings(), ...settings };
    writePending(merged);
    audit('settings_staged', session.username, JSON.stringify(settings));
    return json(res, 200, { pending: merged });
  }
  if (req.method === 'POST' && url.pathname === '/api/jobs') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    if (input.type === 'backup') {
      return json(res, 202, { id: enqueueJob('backup', session.username) });
    }
    if (!['apply', 'restore', 'restart'].includes(input.type)) throw new HttpError(400, 'Opération inconnue.');
    if (!(await checkPasswordAsync(input.password, readUsers()[session.username]))) throw new HttpError(403, 'Mot de passe incorrect.');
    if (input.type === 'restart') return json(res, 202, { id: scheduledRestart()?.id || enqueueJob('restart', session.username) });
    const players = await rcon('list');
    if (!/\b0 of a max of \d+ players online\b/.test(players)) throw new HttpError(409, 'Attends que tous les joueurs soient déconnectés.');
    if (input.type === 'apply') {
      const settings = pendingSettings();
      if (!Object.keys(settings).length) throw new HttpError(400, 'Aucun réglage en attente.');
      return json(res, 202, { id: enqueueJob('apply', session.username, { settings }) });
    }
    if (input.confirm !== 'RESTAURER') throw new HttpError(400, 'Confirmation manquante.');
    backupFile(String(input.backup || ''));
    return json(res, 202, { id: enqueueJob('restore', session.username, { backup: input.backup }) });
  }
  if (req.method === 'POST' && url.pathname === '/api/command') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const command = String(input.command || '').trim().replace(/^\//, '');
    if (!command || command.length > 240 || /[\r\n\0]/.test(command)) throw new HttpError(400, 'Commande invalide.');
    if (/^(reload|stop|restart|save-off)(\s|$)/i.test(command) || /^paper\s+reload(\s|$)/i.test(command)) {
      throw new HttpError(400, 'Cette commande exige un redémarrage ou risque de bloquer les sauvegardes.');
    }
    const result = await rcon(command);
    audit('command', session.username, command);
    return json(res, 200, { result });
  }
  if (req.method === 'POST' && url.pathname === '/api/items/give') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const player = String(input.player || '').trim();
    const item = String(input.item || '').trim().toLowerCase();
    const count = Number(input.count);
    if (!/^[A-Za-z0-9_]{3,16}$/.test(player) || !/^(?:minecraft:)?[a-z0-9_]+$/.test(item)
        || !Number.isInteger(count) || count < 1 || count > 64) {
      throw new HttpError(400, 'Pseudo, identifiant d’objet ou quantité invalide (1 à 64).');
    }
    const command = `give ${player} ${item.includes(':') ? item : `minecraft:${item}`} ${count}`;
    const result = await rcon(command);
    audit('give_item', session.username, `${player} ${item} ${count}`);
    return json(res, 200, { result });
  }
  if (req.method === 'POST' && url.pathname === '/api/items/action') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const player = String(input.player || '').trim();
    const item = String(input.item || '').trim().toLowerCase();
    const id = item.includes(':') ? item : `minecraft:${item}`;
    const count = Number(input.count);
    const mode = input.mode;
    if (!/^[A-Za-z0-9_]{3,16}$/.test(player) || !ITEM_IDS.has(id)
        || !Number.isInteger(count) || count < 1 || count > 64 || !['give', 'drop'].includes(mode)) {
      throw new HttpError(400, 'Choisis un joueur, un objet du catalogue et une quantité de 1 à 64.');
    }
    const command = mode === 'give' ? `give ${player} ${id} ${count}`
      : `execute at ${player} run summon item ^ ^1 ^2 {Item:{id:"${id}",count:${count}}}`;
    const result = await rcon(command);
    audit(`item_${mode}`, session.username, `${player} ${id} ${count}`);
    return json(res, 200, { result });
  }
  if (req.method === 'GET' && url.pathname === '/api/towers/catalog') {
    requireSession(req);
    let active = false;
    try { active = (await rcon('dctower')).includes('Usage : dctower'); } catch {}
    const presets = [
      { id: 'medieval', name: 'Bastion médiéval', style: 'Pierre et bois', width: 3, height: 6, length: 3, range: 32, source: 'preset', defensive: true },
      { id: 'fantasy', name: 'Tour arcanique', style: 'Améthyste et purpur', width: 3, height: 6, length: 3, range: 32, source: 'preset', defensive: true },
      { id: 'modern', name: 'Poste moderne', style: 'Fer et cuivre', width: 3, height: 6, length: 3, range: 32, source: 'preset', defensive: true },
    ];
    const personal = fs.readdirSync(TOWER_SCHEMATICS_DIR).filter((name) => /^[a-f0-9]{16}\.json$/.test(name))
      .map((name) => {
        try { return JSON.parse(fs.readFileSync(path.join(TOWER_SCHEMATICS_DIR, name), 'utf8')); }
        catch { return null; }
      }).filter((entry) => entry && /^[a-f0-9]{16}$/.test(entry.id) && fs.existsSync(path.join(TOWER_SCHEMATICS_DIR, `${entry.id}.schem`)))
      .map((entry) => ({ ...entry, defensive: false }));
    return json(res, 200, { active, towers: [...presets, ...personal] });
  }
  if (req.method === 'POST' && url.pathname === '/api/towers/upload') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req, 1450000);
    const filename = String(input.filename || '').trim();
    const encoded = String(input.data || '');
    if (!/^[^\\/\r\n\0]{1,80}\.schem$/i.test(filename) || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded))
      throw new HttpError(400, 'Choisis un fichier .schem valide.');
    const content = Buffer.from(encoded, 'base64');
    if (!content.length || content.length > 1024 * 1024 || content[0] !== 0x1f || content[1] !== 0x8b)
      throw new HttpError(400, 'Le .schem doit être compressé et faire au maximum 1 Mio.');
    if (!(await rcon('dctower')).includes('Usage : dctower'))
      throw new HttpError(503, 'Les constructions seront disponibles après la sauvegarde et la relance de Minecraft. La relance attend que les joueurs soient déconnectés.');
    const id = crypto.randomBytes(8).toString('hex');
    const file = path.join(TOWER_SCHEMATICS_DIR, `${id}.schem`);
    fs.writeFileSync(file, content, { flag: 'wx', mode: 0o600 });
    try {
      const response = await rcon(`dctower inspect ${id}`, 30000);
      const marker = response.indexOf('DCTOWER:');
      if (marker < 0 || response.slice(marker).includes('DCTOWER:Erreur')) throw new HttpError(409, response || 'Le plugin de construction sera actif après la relance du jeu.');
      const preview = JSON.parse(response.slice(marker + 8).trim());
      if (!Number.isInteger(preview.width) || !Number.isInteger(preview.height) || !Number.isInteger(preview.length)
          || !/^[.SWGLMB/]{271}$/.test(preview.preview || '')) throw new HttpError(502, 'Aperçu du .schem invalide.');
      const tower = { id, name: filename.slice(0, -6), style: 'Votre .schem', source: 'personal', defensive: false, ...preview };
      fs.writeFileSync(path.join(TOWER_SCHEMATICS_DIR, `${id}.json`), JSON.stringify(tower), { flag: 'wx', mode: 0o600 });
      audit('tower_uploaded', session.username, `${id} ${filename}`);
      return json(res, 201, { tower });
    } catch (error) { fs.unlinkSync(file); throw error; }
  }
  if (req.method === 'POST' && url.pathname === '/api/towers/place') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const id = String(input.id || '');
    const world = String(input.world || '');
    const x = Number(input.x), z = Number(input.z);
    const preset = ['medieval', 'fantasy', 'modern'].includes(id);
    const personal = /^[a-f0-9]{16}$/.test(id) && fs.existsSync(path.join(TOWER_SCHEMATICS_DIR, `${id}.json`));
    if ((!preset && !personal) || !MOB_WORLDS[world] || !Number.isInteger(x) || !Number.isInteger(z)
        || Math.abs(x) > 29999900 || Math.abs(z) > 29999900) throw new HttpError(400, 'Choisis une construction et un point valide sur la carte.');
    if (!(await rcon('dctower')).includes('Usage : dctower'))
      throw new HttpError(503, 'Les constructions seront disponibles après la sauvegarde et la relance de Minecraft. La relance attend que les joueurs soient déconnectés.');
    const result = await rcon(`dctower place ${id} ${world} ${x} ${z}`, 45000);
    if (!/DCTOWER:(?:Tour|Construction) posée/.test(result)) throw new HttpError(409, result || 'La pose de la construction a échoué.');
    audit('tower_placed', session.username, `${id} ${world} ${x} ${z}`);
    return json(res, 200, { result: result.slice(result.indexOf('DCTOWER:') + 8) });
  }
  if (req.method === 'POST' && url.pathname === '/api/towers/check') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const id = String(input.id || '');
    const world = String(input.world || '');
    const x = Number(input.x), z = Number(input.z);
    const preset = ['medieval', 'fantasy', 'modern'].includes(id);
    const personal = /^[a-f0-9]{16}$/.test(id) && fs.existsSync(path.join(TOWER_SCHEMATICS_DIR, `${id}.json`));
    if ((!preset && !personal) || !MOB_WORLDS[world] || !Number.isInteger(x) || !Number.isInteger(z)
        || Math.abs(x) > 29999900 || Math.abs(z) > 29999900) throw new HttpError(400, 'Choisis une construction et un point valide sur la carte.');
    const result = await rcon(`dctower check ${id} ${world} ${x} ${z}`, 45000);
    if (!result.includes('DCTOWER:Emplacement prêt')) throw new HttpError(409, result || 'Emplacement indisponible.');
    return json(res, 200, { result: result.slice(result.indexOf('DCTOWER:') + 8) });
  }
  if (req.method === 'POST' && url.pathname === '/api/mobs/spawn') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const mob = String(input.mob || '').trim();
    const world = String(input.world || '').trim();
    const x = Number(input.x);
    const z = Number(input.z);
    const count = Number(input.count);
    if (!MOB_IDS.has(mob) || !MOB_WORLDS[world] || !Number.isInteger(x) || !Number.isInteger(z)
        || Math.abs(x) > 29999900 || Math.abs(z) > 29999900 || !Number.isInteger(count) || count < 1 || count > 8) {
      throw new HttpError(400, 'Choisis une créature, un emplacement sur la carte et une quantité de 1 à 8.');
    }
    const offsets = [[0, 0], [2, 0], [-2, 0], [0, 2], [0, -2], [2, 2], [-2, 2], [2, -2]];
    const commands = offsets.slice(0, count).map(([dx, dz]) =>
      `execute in ${MOB_WORLDS[world]} positioned ${x + dx + 0.5} 0 ${z + dz + 0.5} positioned over motion_blocking_no_leaves run summon minecraft:${mob} ~ ~1 ~`);
    const results = await rconBatch(commands, 20000);
    const spawned = results.filter((result) => /summoned|invoked|apparu|invoqu/i.test(result)).length;
    audit('mob_spawn', session.username, `${mob} ${count} ${world} ${x} ${z} successful=${spawned}`);
    if (spawned === 0) throw new HttpError(409, results[0] || 'Impossible de faire apparaître cette créature à cet endroit.');
    return json(res, 200, { spawned, requested: count, result: results[0] });
  }
  if (req.method === 'POST' && url.pathname === '/api/custom-items') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const slug = String(input.slug || '').trim().toLowerCase();
    const name = String(input.name || '').trim();
    const lore = String(input.lore || '').trim();
    const item = String(input.item || '').trim().toLowerCase();
    if (!/^[a-z][a-z0-9_]{2,31}$/.test(slug) || name.length < 3 || name.length > 40 || lore.length > 100
        || /[<>\r\n\0]/.test(name + lore) || !ITEM_IDS.has(item)) {
      throw new HttpError(400, 'Identifiant, nom, description ou objet de base invalide.');
    }
    const id = `dc_${slug}`;
    const filename = path.join(CUSTOM_ITEMS_DIR, `${id}.yml`);
    const yaml = `id: ${id}\nmaterial: ${item.slice('minecraft:'.length).toUpperCase()}\ndisplay-name: ${JSON.stringify(name)}\nlore:\n  - ${JSON.stringify(lore || 'Objet DuckCatMine')}\nversion: 1\ntags: [duckcatmine]\n`;
    try { fs.writeFileSync(filename, yaml, { flag: 'wx', mode: 0o600 }); }
    catch (error) {
      if (error.code === 'EEXIST') throw new HttpError(409, 'Cet identifiant existe déjà.');
      throw error;
    }
    try {
      const result = await rcon('mcraft reload');
      if (/unknown command|not found|error|failed/i.test(result)) throw new HttpError(409, result);
      audit('custom_item_created', session.username, `${id} ${item}`);
      return json(res, 201, { id, result: result || 'Objet créé et catalogue rechargé.' });
    } catch (error) { fs.unlinkSync(filename); throw error; }
  }
  if (req.method === 'POST' && url.pathname === '/api/custom-items/give') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const id = String(input.id || '').trim();
    const player = String(input.player || '').trim();
    const count = Number(input.count);
    if (!customItems().some((item) => item.id === id) || !/^[A-Za-z0-9_]{3,16}$/.test(player)
        || !Number.isInteger(count) || count < 1 || count > 64) throw new HttpError(400, 'Objet, joueur ou quantité invalide.');
    const result = await rcon(`mcraft give ${id} ${count} ${player}`);
    if (/unknown command|not found|not online|error|failed/i.test(result)) throw new HttpError(409, result);
    audit('custom_item_given', session.username, `${id} ${count} ${player}`);
    return json(res, 200, { result: result || 'Objet personnalisé donné.' });
  }
  if (req.method === 'POST' && url.pathname === '/api/trains/chest') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const player = String(input.player || '').trim();
    const model = String(input.model || '').trim();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(player) || !['loco', 'loco_anim', 'carr', 'carg', 'carb'].includes(model)) {
      throw new HttpError(400, 'Joueur ou modèle de train invalide.');
    }
    const result = await rcon(`train chest give ${player} --finite --locked --train ${model}`);
    if (/unknown command|failed to find|not found|no player/i.test(result)) throw new HttpError(409, result);
    audit('train_chest', session.username, `${player} ${model}`);
    return json(res, 200, { result: result || 'Coffre de train donné.' });
  }
  if (req.method === 'POST' && url.pathname === '/api/skins/apply') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const player = String(input.player || '').trim();
    const skin = String(input.skin || '').trim();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(player) || !/^[A-Za-z0-9_]{3,16}$/.test(skin)) {
      throw new HttpError(400, 'Joueur ou pseudo du skin invalide.');
    }
    const result = await rcon(`skin set ${skin} ${player}`);
    if (/unknown command|failed to find|not found|no player|invalid skin/i.test(result)) throw new HttpError(409, result);
    audit('skin_apply', session.username, `${player} ${skin}`);
    return json(res, 200, { result: result || 'Changement de skin demandé. Patiente quelques secondes.' });
  }
  if (req.method === 'POST' && url.pathname === '/api/skins/apply-gallery') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const player = String(input.player || '').trim();
    const id = String(input.id || '').trim();
    const source = String(input.source || 'craftdex');
    if (!/^[A-Za-z0-9_]{3,16}$/.test(player) || !['craftdex', 'mineskin'].includes(source) ||
      !(source === 'craftdex' ? /^[a-f0-9-]{36}$/.test(id) : /^[a-f0-9]{32}$/.test(id))) throw new HttpError(400, 'Joueur ou skin invalide.');
    const plugins = await rcon('plugins');
    if (!plugins.includes('SkinsRestorer')) throw new HttpError(409, 'SkinsRestorer sera disponible après la relance programmée du jeu.');
    const detail = JSON.parse((await skinSource(source === 'craftdex'
      ? `https://craftdex.net/skins/${id}.json`
      : `https://api.mineskin.org/v2/skins/${id}`, 256 * 1024)).data.toString('utf8'));
    const hash = source === 'craftdex' ? detail.hash : detail.skin?.texture?.hash?.skin;
    const variant = source === 'craftdex' ? (detail.slim ? 'slim' : 'classic') : (detail.skin?.variant === 'slim' ? 'slim' : 'classic');
    if ((source === 'craftdex' && (detail.banned || detail.id !== id)) ||
      (source === 'mineskin' && (!detail.success || detail.skin?.uuid !== id || detail.skin?.visibility !== 'public')) ||
      !/^[a-f0-9]{64}$/.test(hash || '') || !['slim', 'classic'].includes(variant)) throw new HttpError(409, 'Ce skin n’est plus disponible.');
    const name = `dc_${id.replaceAll('-', '').slice(0, 12)}`;
    const created = await rcon(`sr createcustom ${name} "https://textures.minecraft.net/texture/${hash}" ${variant}`, 45000);
    if (/unknown command|error|failed|invalid|cannot|could not/i.test(created)) throw new HttpError(409, created.slice(0, 300));
    const result = await rcon(`skin set ${name} ${player}`);
    if (/unknown command|error|failed|not found|invalid|no player/i.test(result)) throw new HttpError(409, result.slice(0, 300) || 'Le skin a été créé, mais son application a échoué.');
    audit('skin_gallery_apply', session.username, `${player} ${source}:${id}`);
    return json(res, 200, { result: result || `Skin appliqué à ${player}.` });
  }
  if (req.method === 'POST' && url.pathname === '/api/events') {
    const session = requireSession(req);
    requireCsrf(req, session);
    const input = await body(req);
    const kind = String(input.kind || '');
    if (!['loot', 'horde', 'stop', 'speed'].includes(kind)) throw new HttpError(400, 'Événement inconnu.');
    const list = await rcon('list');
    const names = (list.match(/players online:\s*(.*)$/i)?.[1] || '').split(',').map((name) => name.trim()).filter(Boolean);
    if (kind !== 'stop' && !names.length) throw new HttpError(409, 'Aucun joueur connecté.');
    const player = String(input.player || '').trim();
    if (['loot', 'horde'].includes(kind) && (!/^[A-Za-z0-9_]{3,16}$/.test(player) || !names.includes(player))) {
      throw new HttpError(400, 'Choisis un joueur connecté.');
    }
    if (kind !== 'stop' && Date.now() < (eventCooldowns.get(kind) || 0)) {
      throw new HttpError(429, 'Attends quelques secondes avant de relancer cet événement.');
    }
    let commands;
    if (kind === 'loot') {
      const item = String(input.item || '').trim().toLowerCase();
      const count = Number(input.count);
      if (!ITEM_IDS.has(item) || !Number.isInteger(count) || count < 1 || count > 8) {
        throw new HttpError(400, 'Choisis un objet du catalogue et une quantité de 1 à 8.');
      }
      const offsets = [[-2, 2], [0, 3], [2, 2], [-1, 4], [1, 4], [-3, 3], [3, 3], [0, 5]];
      commands = offsets.slice(0, count).map(([x, z]) =>
        `execute at ${player} run summon item ^${x} ^3 ^${z} {Item:{id:"${item}",count:1}}`);
    } else if (kind === 'horde') {
      commands = [[-4, 4], [4, 4], [-4, -4], [4, -4]].map(([x, z]) =>
        `execute at ${player} run summon minecraft:zombie ^${x} ^ ^${z} {Tags:["duckcatmine_event"]}`);
    } else if (kind === 'speed') {
      commands = ['effect give @a minecraft:speed 60 1 true', 'say Défi vitesse : 60 secondes !'];
    } else {
      commands = ['kill @e[tag=duckcatmine_event]'];
    }
    if (kind !== 'stop') eventCooldowns.set(kind, Date.now() + (kind === 'horde' ? 60_000 : 20_000));
    try {
      const results = await rconBatch(commands);
      if (results.some((value) => /unknown command|incorrect argument|expected whitespace/i.test(value))) {
        throw new HttpError(409, results.join(' ').slice(0, 300));
      }
      audit(`event_${kind}`, session.username, kind === 'loot' ? `${player} ${input.item} ${input.count}` : player);
      return json(res, 200, { result: ({ loot: 'Pluie d’objets lancée.', horde: 'Quatre zombies invoqués. Le bouton Arrêter les retire.', speed: 'Défi vitesse lancé pour 60 secondes.', stop: 'Monstres de l’événement retirés.' })[kind] });
    } catch (error) {
      if (kind !== 'stop') eventCooldowns.delete(kind);
      throw error;
    }
  }
  throw new HttpError(404, 'Introuvable.');
}

http.createServer((req, res) => {
  serve(req, res).catch((error) => {
    if (res.headersSent) return res.destroy();
    securityHeaders(res);
    const status = error instanceof HttpError ? error.status : 500;
    if (status === 500) console.error(error);
    json(res, status, { error: status === 500 ? 'Erreur interne.' : error.message });
  });
}).listen(PORT, '0.0.0.0', () => console.log(`DuckCatMine admin listening on ${PORT}`));

let lastChatEventId = '';
let eventTickBusy = false;
setInterval(async () => {
  if (eventTickBusy) return;
  eventTickBusy = true;
  try {
    for (const client of eventClients) {
      if (!sessions.has(client.session.id) || client.session.expires <= Date.now()) { client.res.end(); eventClients.delete(client); }
    }
    const messages = await recentChat();
    const id = messages.at(-1)?.id || '';
    if (id !== lastChatEventId) { lastChatEventId = id; broadcast('chat', { messages }); }
    await monitorPushChat();
  } catch (error) { console.error('Event stream:', error.message); }
  finally { eventTickBusy = false; }
}, 1000).unref();
setInterval(async () => {
  if (!eventClients.size) return;
  try { broadcast('status', await statusSnapshot.get()); } catch {}
}, 5000).unref();
setInterval(() => {
  for (const client of eventClients) client.res.write(': keepalive\n\n');
}, 15000).unref();
