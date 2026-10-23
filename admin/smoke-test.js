'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const { spawn, spawnSync } = require('node:child_process');

test('activation, login, session, CSRF and logout', async () => {
  const state = fs.mkdtempSync(path.join(os.tmpdir(), 'duckcatmine-admin-test-'));
  const mcdata = path.join(state, 'mc-data');
  fs.mkdirSync(path.join(mcdata, 'logs'), { recursive: true });
  fs.writeFileSync(path.join(mcdata, '.papermc-manifest.json'), JSON.stringify({ minecraftVersion: '26.3' }));
  fs.writeFileSync(path.join(mcdata, 'logs', 'latest.log'), [
    '[18:01:02] [Async Chat Thread - #2/INFO]: <Alex> Salut la team',
    '[18:01:03] [Server thread/INFO]: [Not Secure] [Rcon] Bienvenue',
    '[18:01:05] [Server thread/INFO]: [Rcon] [Web:Alex] Depuis le panneau',
    '[18:01:04] [Server thread/INFO]: IP privée: 127.0.0.1',
  ].join('\n'));
  const socket = net.createServer();
  await new Promise((resolve) => socket.listen(0, '127.0.0.1', resolve));
  const port = socket.address().port;
  await new Promise((resolve) => socket.close(resolve));
  const origin = `http://127.0.0.1:${port}`;
  const env = { ...process.env, STATE_DIR: state, MC_DATA_DIR: mcdata, PUBLIC_ORIGIN: origin, PORT: String(port) };
  const rconCommands = [];
  const rconPacket = (id, type, value = '') => {
    const payload = Buffer.from(value);
    const packet = Buffer.alloc(14 + payload.length);
    packet.writeInt32LE(10 + payload.length, 0);
    packet.writeInt32LE(id, 4);
    packet.writeInt32LE(type, 8);
    payload.copy(packet, 12);
    return packet;
  };
  const rconServer = net.createServer((connection) => {
    let pending = Buffer.alloc(0);
    connection.on('data', (chunk) => {
      pending = Buffer.concat([pending, chunk]);
      while (pending.length >= 4 && pending.length >= pending.readInt32LE(0) + 4) {
        const size = pending.readInt32LE(0);
        const id = pending.readInt32LE(4);
        const type = pending.readInt32LE(8);
        const value = pending.subarray(12, size + 2).toString('utf8');
        pending = pending.subarray(size + 4);
        if (type === 3) connection.write(rconPacket(id, 2));
        if (type === 2) {
          rconCommands.push(value);
          const reply = value === 'dctower' ? 'Usage : dctower inspect <id> | check/place <id> <monde> <x> <z>'
            : value.startsWith('dctower inspect ') ? `DCTOWER:${JSON.stringify({ width: 3, height: 3, length: 3,
              preview: Array(16).fill('S'.repeat(16)).join('/') })}`
              : value.startsWith('dctower check ') ? 'DCTOWER:Emplacement prêt en world X 100 Z 100 sur terrain plat.'
                : value.startsWith('dctower place ') ? 'DCTOWER:Construction posée en world X 100 Y 63 Z 100. Construction classique.'
                  : value === 'dcpanel status Zed' ? 'DCPLAYER:{"name":"Zed","world":"ground_zero","scenario":"ground_zero"}'
                    : value === 'dcpanel heal Zed' ? 'DCPLAYER:{"error":"scenario"}'
                      : 'OK';
          connection.write(rconPacket(id, 0, reply));
        }
      }
    });
  });
  await new Promise((resolve) => rconServer.listen(0, '127.0.0.1', resolve));
  const passwordFile = path.join(state, 'rcon-password');
  fs.writeFileSync(passwordFile, 'test-password');
  env.RCON_HOST = '127.0.0.1';
  env.RCON_PORT = String(rconServer.address().port);
  env.RCON_PASSWORD_FILE = passwordFile;
  let server;
  try {
    const invitation = spawnSync(process.execPath, ['server.js', 'invite', 'Alex'], { cwd: __dirname, env, encoding: 'utf8' });
    assert.equal(invitation.status, 0, invitation.stderr);
    const token = new URLSearchParams(new URL(invitation.stdout.trim()).hash.slice(1)).get('token');
    assert.match(token, /^[a-f0-9]{64}$/);
    server = spawn(process.execPath, ['server.js'], { cwd: __dirname, env, stdio: 'ignore' });
    for (let i = 0; i < 40; i++) {
      try { if ((await fetch(`${origin}/healthz`)).ok) break; } catch {}
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
    const post = (endpoint, data, headers = {}) => fetch(`${origin}${endpoint}`, {
      method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(data),
    });
    assert.equal((await post('/api/invite-info', { token })).status, 200);
    assert.equal((await post('/api/activate', { token, password: 'une-longue-phrase-secrete-123' })).status, 200);
    assert.equal((await post('/api/activate', { token, password: 'une-longue-phrase-secrete-123' })).status, 400);
    assert.equal((await post('/api/login', { username: 'Alex', password: 'incorrect' })).status, 401);
    const login = await post('/api/login', { username: 'Alex', password: 'une-longue-phrase-secrete-123' });
    assert.equal(login.status, 200);
    const csrf = (await login.json()).csrf;
    const cookie = login.headers.get('set-cookie').split(';')[0];
    assert.equal((await fetch(`${origin}/api/me`, { headers: { Cookie: cookie } })).status, 200);
    assert.equal((await fetch(`${origin}/api/status`)).status, 401);
    assert.equal((await fetch(`${origin}/api/events`)).status, 401);
    const warm = await fetch(`${origin}/api/status`, { headers: { Cookie: cookie } });
    assert.equal(warm.status, 200);
    assert.equal((await warm.json()).gameVersion, '26.3');
    const collected = rconCommands.length;
    const started = performance.now();
    const simultaneous = await Promise.all(Array.from({ length: 30 }, async () => {
      const r = await fetch(`${origin}/api/status`, { headers: { Cookie: cookie } });
      assert.equal(r.status, 200);
      return r.json();
    }));
    assert.equal(simultaneous.length, 30);
    assert.equal(rconCommands.length, collected, 'cached requests must not issue any new Minecraft commands');
    console.log(`30 cached status requests: ${Math.round(performance.now() - started)} ms total; 0 extra RCON commands`);
    const stream = await fetch(`${origin}/api/events`, { headers: { Cookie: cookie } });
    assert.match(stream.headers.get('content-type'), /text\/event-stream/);
    const reader = stream.body.getReader();
    const firstEvent = await reader.read();
    assert.match(new TextDecoder().decode(firstEvent.value), /event: chat/);
    await reader.cancel();
    assert.equal((await fetch(`${origin}/api/skins/catalog`)).status, 401);
    assert.equal((await fetch(`${origin}/api/chat`)).status, 401);
    assert.equal((await fetch(`${origin}/api/push/config`)).status, 401);
    assert.equal((await fetch(`${origin}/map-picker.js`)).status, 200);
    assert.equal((await fetch(`${origin}/api/players/Alex/details`)).status, 401);
    assert.equal((await fetch(`${origin}/api/map-auth`)).status, 401);
    const chat = await fetch(`${origin}/api/chat`, { headers: { Cookie: cookie } });
    assert.equal(chat.status, 200);
    const chatMessages = (await chat.json()).messages;
    assert.deepEqual(chatMessages.map(({ id, ...entry }) => entry), [
      { at: '18:01:02', author: 'Alex', message: 'Salut la team' },
      { at: '18:01:03', author: 'Serveur', message: 'Bienvenue' },
      { at: '18:01:05', author: 'Alex', message: 'Depuis le panneau', source: 'web' },
    ]);
    assert.match(chatMessages[0].id, /^\d+:\d+$/);
    fs.appendFileSync(path.join(mcdata, 'logs', 'latest.log'), '\n[18:01:02] [Async Chat Thread - #2/INFO]: <Alex> Salut la team');
    const updatedChat = (await (await fetch(`${origin}/api/chat`, { headers: { Cookie: cookie } })).json()).messages;
    assert.equal(updatedChat.length, 4);
    assert.equal(updatedChat[0].id, chatMessages[0].id);
    assert.notEqual(updatedChat[3].id, chatMessages[0].id);
    assert.equal((await post('/api/chat/send', { message: 'Salut' }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/chat/send', { message: 'Bonjour\n/op' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/chat/send', { message: 'Salut du téléphone' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert(rconCommands.includes('say [Web:Alex] Salut du téléphone'));
    const pushConfig = await fetch(`${origin}/api/push/config`, { headers: { Cookie: cookie } });
    assert.equal(pushConfig.status, 200);
    assert.match((await pushConfig.json()).publicKey, /^[A-Za-z0-9_-]{80,100}$/);
    assert.equal((await post('/api/push/subscribe', { subscription: {} }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    const subscription = { endpoint: 'https://fcm.googleapis.com/fcm/send/test',
      keys: { p256dh: 'A'.repeat(87), auth: 'B'.repeat(22) } };
    assert.equal((await post('/api/push/subscribe', { subscription }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/push/subscribe', { subscription }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert.equal(JSON.parse(fs.readFileSync(path.join(state, 'push-subscriptions.json')))[0].username, 'Alex');
    assert.equal((await post('/api/push/unsubscribe', { endpoint: subscription.endpoint }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert.deepEqual(JSON.parse(fs.readFileSync(path.join(state, 'push-subscriptions.json'))), []);
    assert.equal((await fetch(`${origin}/api/map-auth`, { headers: { Cookie: cookie } })).status, 200);
    const catalog = await fetch(`${origin}/api/items/catalog`, { headers: { Cookie: cookie } });
    assert.equal(catalog.status, 200);
    assert((await catalog.json()).items.includes('minecraft:diamond'));
    const towers = await fetch(`${origin}/api/towers/catalog`, { headers: { Cookie: cookie } });
    assert.equal(towers.status, 200);
    const initialTowers = await towers.json();
    assert.equal(initialTowers.active, true);
    assert.equal(initialTowers.towers.length, 3);
    assert(initialTowers.towers.every((tower) => tower.defensive));
    const sampleSchematic = fs.readFileSync(path.join(__dirname, '..', 'tests', 'fixtures', 'sample.schem'));
    const upload = await post('/api/towers/upload', { filename: 'Maison.schem', data: sampleSchematic.toString('base64') },
      { Cookie: cookie, 'X-CSRF-Token': csrf });
    assert.equal(upload.status, 201);
    const personal = (await upload.json()).tower;
    assert.equal(personal.defensive, false);
    assert.equal(personal.name, 'Maison');
    assert.equal((await post('/api/towers/check', { id: personal.id, world: 'world', x: 100, z: 100 },
      { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert.equal((await post('/api/towers/place', { id: personal.id, world: 'world', x: 100, z: 100 },
      { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    const updatedTowers = await (await fetch(`${origin}/api/towers/catalog`, { headers: { Cookie: cookie } })).json();
    assert.equal(updatedTowers.towers.at(-1).defensive, false);
    assert.equal((await fetch(`${origin}/api/skins/catalog?tag=../escape`, { headers: { Cookie: cookie } })).status, 400);
    if (process.env.TEST_SKIN_SOURCE === '1') {
      const gallery = await fetch(`${origin}/api/skins/catalog?query=knight`, { headers: { Cookie: cookie } });
      assert.equal(gallery.status, 200);
      const search = await gallery.json();
      assert.equal(search.source, 'https://mineskin.org');
      const skins = search.skins;
      assert(skins.length > 0);
      assert.equal(skins[0].source, 'mineskin');
      const detail = await (await fetch(`https://api.mineskin.org/v2/skins/${skins[0].id}`, {
        headers: { 'User-Agent': 'DuckCatMineAdmin/1.0 (skin gallery)' },
      })).json();
      assert.equal(detail.skin.texture.hash.skin, skins[0].hash);
      const preview = await fetch(`${origin}/api/skins/texture/${skins[0].hash}`, { headers: { Cookie: cookie } });
      assert.equal(preview.status, 200);
      assert.equal(preview.headers.get('content-type'), 'image/png');
      assert.equal((await fetch(`${origin}/api/skins/player-image/Notch`, { headers: { Cookie: cookie } })).status, 200);
    }
    assert.equal((await fetch(`${origin}/api/custom-items`, { headers: { Cookie: cookie } })).status, 200);
    assert.equal((await post('/api/custom-items', { slug: '../evil', name: 'Bad item', item: 'minecraft:diamond' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/custom-items/give', { id: 'dc_unknown', player: 'Alex', count: 1 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/events', { kind: 'command' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/items/give', { player: 'Alex', item: 'diamond', count: 1 }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/items/give', { player: 'Alex', item: 'diamond;op', count: 1 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/items/action', { player: 'Alex', item: 'minecraft:not_a_real_item', count: 1, mode: 'drop' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/items/action', { player: 'Alex', item: 'minecraft:diamond', count: 1, mode: 'drop' }, { Cookie: cookie })).status, 403);
    const beforeScenarioChecks = rconCommands.length;
    assert.equal((await post('/api/items/give', { player: 'Zed', item: 'diamond', count: 1 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 409);
    assert.equal((await post('/api/items/action', { player: 'Zed', item: 'minecraft:diamond', count: 1, mode: 'drop' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 409);
    const scenarioHeal = await post('/api/players/live-action', { player: 'Zed', operation: 'heal' }, { Cookie: cookie, 'X-CSRF-Token': csrf });
    assert.equal(scenarioHeal.status, 409);
    assert.match((await scenarioHeal.json()).error, /Ground Zero/);
    assert(!rconCommands.slice(beforeScenarioChecks).some((command) => command.startsWith('give Zed') || command.includes('summon item')),
      'no item may reach a Ground Zero survivor from the panel');
    assert.equal((await fetch(`${origin}/api/apocalypse`, { headers: { Cookie: cookie } })).status, 200);
    assert.equal((await (await fetch(`${origin}/api/apocalypse`, { headers: { Cookie: cookie } })).json()).installed, false);
    const apocalypseDir = path.join(mcdata, 'plugins', 'DuckCatApocalypse');
    fs.mkdirSync(apocalypseDir, { recursive: true });
    fs.writeFileSync(path.join(apocalypseDir, 'status.json'), JSON.stringify({ version: 1, generatedAt: Date.now(), running: true,
      enabled: true, world: 'ground_zero', threat: { level: 2.5, phase: 'pressure', phaseLabel: 'pression', secondsLeft: 40, hordes: 1 },
      rules: { pvp: false, destruction: true, globalCap: 60 }, infected: { walker: 3 }, infectedTotal: 3,
      survivors: [{ name: 'Mia', x: 10, z: -4, points: 30, infectionSeconds: null }], teams: [] }));
    assert.equal((await fetch(`${origin}/api/apocalypse`)).status, 401);
    const apocalypse = await (await fetch(`${origin}/api/apocalypse`, { headers: { Cookie: cookie } })).json();
    assert.equal(apocalypse.installed, true);
    assert.equal(apocalypse.stale, false);
    assert.equal(apocalypse.survivors[0].name, 'Mia');
    assert.equal((await fetch(`${origin}/api/apocalypse/map.png`, { headers: { Cookie: cookie } })).status, 404);
    const beforeSurvivorGive = rconCommands.length;
    assert.equal((await post('/api/items/give', { player: 'Mia', item: 'diamond', count: 1 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 409,
      'a survivor listed by the plugin is protected even without the bridge');
    assert(!rconCommands.slice(beforeSurvivorGive).some((command) => command.startsWith('give Mia')));
    assert.equal((await post('/api/apocalypse/action', { action: 'threat', value: 3 }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/apocalypse/action', { action: 'threat', value: 3.14 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert(rconCommands.includes('apocalypse admin threat 3.1'));
    assert.equal((await post('/api/apocalypse/action', { action: 'threat', value: 99 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/apocalypse/action', { action: 'pvp', value: 'on' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/apocalypse/action', { action: 'pvp', value: true }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert(rconCommands.includes('apocalypse admin pvp on'));
    assert.equal((await post('/api/apocalypse/action', { action: 'horde', value: 'Mia;op' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/apocalypse/action', { action: 'map', value: 5000 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/apocalypse/action', { action: 'world recreate confirm' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    fs.writeFileSync(path.join(apocalypseDir, 'status.json'), JSON.stringify({ version: 1, generatedAt: Date.now() - 120000, running: true,
      survivors: [{ name: 'Mia' }] }));
    const stale = await (await fetch(`${origin}/api/apocalypse`, { headers: { Cookie: cookie } })).json();
    assert.equal(stale.stale, true);
    assert.deepEqual(stale.survivors, [], 'an old status never lists survivors as present');
    assert.equal((await post('/api/mobs/spawn', { mob: 'cow', world: 'world', x: 0, z: 0, count: 1 }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/mobs/spawn', { mob: 'cow;op', world: 'world', x: 0, z: 0, count: 1 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/mobs/spawn', { mob: 'cow', world: 'elsewhere', x: 0, z: 0, count: 1 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/mobs/spawn', { mob: 'cow', world: 'world', x: 0, z: 0, count: 99 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/towers/place', { id: 'other', world: 'world', x: 0, z: 0 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/towers/check', { id: 'other', world: 'world', x: 0, z: 0 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/towers/upload', { filename: '../bad.schem', data: 'SGVsbG8=' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/players/live-action', { player: 'Alex', operation: 'heal' }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/players/live-action', { player: 'Alex', operation: 'clear', slot: 'main:99', rev: '0' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/players/live-action', { player: 'Alex', operation: 'set', slot: 'main:0', rev: '0', item: 'minecraft:diamond;op', count: 1 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/trains/chest', { player: 'Alex', model: 'loco;op' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/skins/apply', { player: 'Alex', skin: 'xknat;op' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/skins/apply-gallery', { player: 'Alex', id: '../escape' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/skins/apply-gallery', { player: 'Alex', id: 'bad', source: 'mineskin' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    assert.equal((await post('/api/settings', { pvp: false }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/settings', { pvp: false, maxPlayers: 12 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert.equal((await post('/api/settings', { viewDistance: 99 }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 400);
    const queued = await post('/api/jobs', { type: 'backup' }, { Cookie: cookie, 'X-CSRF-Token': csrf });
    assert.equal(queued.status, 202);
    const jobId = (await queued.json()).id;
    assert.equal((await fetch(`${origin}/api/jobs/${jobId}`, { headers: { Cookie: cookie } })).status, 200);
    assert.equal((await post('/api/jobs', { type: 'restart', password: 'incorrect' }, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 403);
    const restart = await post('/api/jobs', { type: 'restart', password: 'une-longue-phrase-secrete-123' }, { Cookie: cookie, 'X-CSRF-Token': csrf });
    assert.equal(restart.status, 202);
    assert.match((await restart.json()).id, /^[a-f0-9]{32}$/);
    assert.equal((await fetch(`${origin}/api/backups/../../users.json`, { headers: { Cookie: cookie } })).status, 404);
    assert.equal((await post('/api/command', { command: 'list' }, { Cookie: cookie })).status, 403);
    assert.equal((await post('/api/logout', {}, { Cookie: cookie, 'X-CSRF-Token': csrf })).status, 200);
    assert.equal((await fetch(`${origin}/api/me`, { headers: { Cookie: cookie } })).status, 401);
  } finally {
    server?.kill();
    await new Promise((resolve) => rconServer.close(resolve));
    fs.rmSync(state, { recursive: true, force: true });
  }
});
