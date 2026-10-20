'use strict';
// docker compose exec -T panel node - < tests/plugins-integration.cjs
// Requires /jobs/plugin-fixture.jar, built from tests/plugin-fixture with Java 25.
const fs = require('node:fs');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
assert.equal(process.env.PUBLIC_ORIGIN, 'http://127.0.0.1:18080', 'Isolated localhost installation required');
const root = 'http://https', usersFile = `${process.env.STATE_DIR}/users.json`;
const username = 'DcTest' + crypto.randomBytes(4).toString('hex'), password = crypto.randomBytes(32).toString('hex');
let cookie = '', csrf = '';
const readUsers = () => JSON.parse(fs.readFileSync(usersFile, 'utf8'));
function saveUsers(value) { const temp = usersFile + '.plugin-test.tmp'; fs.writeFileSync(temp, JSON.stringify(value), { mode: 0o600 }); fs.renameSync(temp, usersFile); }
async function request(url, method = 'GET', body, raw = false) {
  return fetch(root + url, { method, headers: { Origin: process.env.PUBLIC_ORIGIN, Cookie: cookie, 'X-CSRF-Token': csrf, 'Content-Type': raw ? 'application/octet-stream' : 'application/json' }, body: body ? raw ? body : JSON.stringify(body) : undefined });
}
async function json(url, method = 'GET', body, raw = false) {
  const response = await request(url, method, body, raw); const result = await response.json(); assert(response.ok, `${url}: ${JSON.stringify(result)}`); return result;
}
async function waitJob(id) {
  for (let i = 0; i < 180; i++) { const r = await json('/api/jobs/' + id); if (r.status === 'done') { assert(r.ok, JSON.stringify(r)); return; } await new Promise(r => setTimeout(r, 1000)); }
  throw new Error('Job timeout');
}
async function gameReady() {
  for (let i = 0; i < 100; i++) { if ((await json('/api/status')).online) return; await new Promise(r => setTimeout(r, 1500)); }
  throw new Error('Minecraft did not return');
}
(async () => {
  try {
    const salt = crypto.randomBytes(16), hash = crypto.scryptSync(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 128 * 1024 * 1024 });
    saveUsers({ ...readUsers(), [username]: { salt: salt.toString('hex'), hash: hash.toString('hex') } });
    const login = await request('/api/login', 'POST', { username, password }); assert(login.ok); cookie = login.headers.get('set-cookie').split(';')[0]; csrf = (await login.json()).csrf;
    const status = await json('/api/status'); assert(status.online); assert.match(status.players, /0 of a max/);
    const inventory = await json('/api/plugins'); assert.equal(inventory.gameVersion, '26.3'); assert(inventory.installed.length >= 5);
    const catalog = await json('/api/plugins/search?query='); assert(catalog.hits.length);
    const candidate = catalog.hits.find(p => !inventory.installed.some(i => i.core && i.name.toLowerCase() === p.name.toLowerCase()));
    assert(candidate);
    const project = await json('/api/plugins/project/' + candidate.id); assert(project.versions.length);
    console.log('Real Modrinth search and compatible version selection: OK');
    const selectedVersion = project.versions.find(v => v.type === 'release') || project.versions[0];
    await waitJob((await json('/api/plugins/install', 'POST', { projectId: project.id, versionId: selectedVersion.id })).id);
    const fromCatalog = (await json('/api/plugins')).pending; assert.equal(fromCatalog.length, 1);
    await waitJob((await json('/api/plugins/cancel', 'POST', { artifact: fromCatalog[0].id })).id);
    console.log('Real Modrinth download, SHA-512 verification, worker validation and cancellation: OK');
    if (process.env.CATALOG_ONLY === '1') return;
    const payload = fs.readFileSync('/jobs/plugin-fixture.jar');
    await waitJob((await json('/api/plugins/upload?filename=DCTestPlugin.jar', 'POST', payload, true)).id);
    let pending = (await json('/api/plugins')).pending; assert.equal(pending.length, 1); assert.equal(pending[0].name, 'DCTestPlugin');
    assert(!fs.existsSync('/mc-data/plugins/DuckCatAddon-DCTestPlugin.jar'));
    await waitJob((await json('/api/plugins/cancel', 'POST', { artifact: pending[0].id })).id);
    assert.equal((await json('/api/plugins')).pending.length, 0);
    console.log('Real streamed upload, metadata validation, unchanged live files, cancellation: OK');
    await waitJob((await json('/api/plugins/upload?filename=DCTestPlugin.jar', 'POST', payload, true)).id);
    await json('/api/settings', 'POST', { motd: status.properties.motd || 'DuckCatMine staging' });
    await waitJob((await json('/api/jobs', 'POST', { type: 'apply', password })).id);
    await gameReady();
    const enabled = await json('/api/command', 'POST', { command: 'version DCTestPlugin' }); assert.match(enabled.result, /DCTestPlugin/); assert.match(enabled.result, /1\.0\.0/);
    assert(fs.readFileSync('/mc-data/logs/latest.log', 'utf8').includes('DUCKCATMINE_PLUGIN_FIXTURE_ENABLED'));
    assert.equal((await json('/api/plugins')).pending.length, 0);
    console.log('Backup, stop, install, restart and actual Paper plugin onEnable: OK');
    const after = await json('/api/status'); const archive = after.backups[0]; assert(archive);
    await waitJob((await json('/api/jobs', 'POST', { type: 'restore', password, backup: archive.name, confirm: 'RESTAURER' })).id);
    await gameReady(); assert(!fs.existsSync('/mc-data/plugins/DuckCatAddon-DCTestPlugin.jar'));
    console.log('Restore backup from before plugin installation, with core plugins retained: OK');
  } finally {
    if (cookie) await request('/api/logout', 'POST').catch(() => {});
    const value = readUsers(); delete value[username]; saveUsers(value);
  }
})().catch(e => { console.error(e); process.exitCode = 1; });
