'use strict';
// Run ONLY in the isolated installation: docker compose exec -T panel node - < tests/integration.cjs
// The test refuses the production project and makes changes to its disposable world.
const fs = require('node:fs');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
assert.equal(process.env.PUBLIC_ORIGIN, 'http://127.0.0.1:18080', 'Isolated localhost installation required');
const base = 'http://https';
const file = `${process.env.STATE_DIR}/users.json`;
const username = `DcTest${crypto.randomBytes(4).toString('hex')}`;
const password = crypto.randomBytes(32).toString('hex');
const salt = crypto.randomBytes(16);
const hash = crypto.scryptSync(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 128 * 1024 * 1024 });
let cookie = '', csrf = '';
function users() { return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {}; }
function save(value) { const temp = `${file}.test-${process.pid}`; fs.writeFileSync(temp, JSON.stringify(value), { mode: 0o600 }); fs.renameSync(temp, file); }
async function request(url, method = 'GET', body) {
  return fetch(base + url, { method, headers: { Origin: process.env.PUBLIC_ORIGIN, Cookie: cookie, 'X-CSRF-Token': csrf, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
}
async function json(url, method = 'GET', body) {
  const reply = await request(url, method, body); const value = await reply.json();
  assert(reply.ok, `${url}: ${reply.status} ${JSON.stringify(value)}`); return value;
}
async function command(command) { return (await json('/api/command', 'POST', { command })).result; }
async function waitJob(id) {
  for (let n = 0; n < 600; n++) {
    const result = await json(`/api/jobs/${id}`);
    if (result.status === 'done') { assert(result.ok, JSON.stringify(result)); return result; }
    await new Promise(r => setTimeout(r, 1000));
  }
  throw new Error('Job timeout');
}
(async () => {
  try {
    assert.equal((await request('/api/status')).status, 401);
    assert.equal((await request('/map/')).status, 401);
    const initial = users(); initial[username] = { salt: salt.toString('hex'), hash: hash.toString('hex') }; save(initial);
    const login = await request('/api/login', 'POST', { username, password }); assert(login.ok);
    cookie = login.headers.get('set-cookie').split(';')[0]; csrf = (await login.json()).csrf;
    const status = await json('/api/status'); assert(status.online); assert.equal(status.mapPolicy, 'idle');
    assert.equal((await request('/map/')).status, 200);
    const mapSettings = await json('/map/settings.json'); assert(mapSettings.maps.length);
    assert.equal((await request('/map-picker.js')).status, 200);
    assert.equal((await request('/icons/diamond.png')).status, 200);
    assert.equal((await json('/api/items/catalog')).items.length, 1657);
    assert((await json('/api/towers/catalog')).active);
    const stream = await request('/api/events'); assert.match(stream.headers.get('content-type'), /text\/event-stream/);
    const reader = stream.body.getReader(); assert.match(new TextDecoder().decode((await reader.read()).value), /event: chat/); await reader.cancel();
    const center = 1000 + crypto.randomInt(1, 100) * 32;
    await command(`forceload add ${center - 8} ${center - 8} ${center + 8} ${center + 8}`);
    await command(`fill ${center - 8} 90 ${center - 8} ${center + 8} 119 ${center + 8} air`);
    await command(`fill ${center - 8} 89 ${center - 8} ${center + 8} 89 ${center + 8} stone`);
    const placement = { id: 'medieval', world: 'world', x: center, z: center };
    await json('/api/towers/check', 'POST', placement);
    await json('/api/towers/place', 'POST', placement);
    await command(`summon zombie ${center + 7} 90 ${center} {Tags:["duckcatmine-test"],NoAI:1b,PersistenceRequired:1b,Invulnerable:0b}`);
    await new Promise(r => setTimeout(r, 3500));
    const defense = await command('dctower status'); assert.match(defense, /[1-9]\d* tirs/);
    await command('kill @e[tag=duckcatmine-test]');
    await command(`forceload remove ${center - 8} ${center - 8} ${center + 8} ${center + 8}`);
    const policy = await json('/api/map/policy', 'POST', { policy: 'idle' }); await waitJob(policy.id);
    const backup = await json('/api/jobs', 'POST', { type: 'backup' }); await waitJob(backup.id);
    await new Promise(r => setTimeout(r, 5100));
    const backups = (await json('/api/status')).backups; assert(backups.length);
    const name = backups[0].name;
    const download = await request(`/api/backups/${name}`); assert(download.ok); assert((await download.arrayBuffer()).byteLength > 1000);
    const restore = await json('/api/jobs', 'POST', { type: 'restore', backup: name, password, confirm: 'RESTAURER' }); await waitJob(restore.id);
    for (let n = 0; n < 180; n++) {
      await new Promise(r => setTimeout(r, 2000));
      if ((await json('/api/status')).online) break;
      if (n === 179) throw new Error('Minecraft did not return after restore');
    }
    assert((await json('/api/towers/catalog')).active, 'construction plugin must survive restore');
    const plugins = await command('plugins');
    for (const plugin of ['BlueMap', 'DuckCatTowers', 'DuckCatWelcome', 'SkinsRestorer', 'WorldEdit']) assert(plugins.includes(plugin), `${plugin} missing after restore`);
    for (let n = 0; n < 60; n++) {
      if ((await request('/map/settings.json')).ok) break;
      await new Promise(r => setTimeout(r, 1000));
      if (n === 59) throw new Error('Map did not return after restore');
    }
    console.log(JSON.stringify({ ok: true, httpsProxy: true, unauthorizedMap: 401, authenticatedMap: 200, mapCount: mapSettings.maps.length, icons: true, eventStream: true, towerPlacement: true, automaticDefense: true, backup: true, backupDownload: true, restore: true, minecraftReturned: true, pluginsSurvivedRestore: true, mapReturned: true }, null, 2));
  } finally {
    if (cookie) await request('/api/logout', 'POST').catch(() => {});
    const value = users(); delete value[username]; save(value);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
