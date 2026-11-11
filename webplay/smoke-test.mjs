import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createServer } from 'node:net';
import test from 'node:test';
import { WebSocket } from 'ws';
import { dialTcp } from '../relay-service/tcp.mjs';
import { createApp } from './app.mjs';
import { duckCatPlay } from './duckcat-play.mjs';
import { JsonAccountAuth } from './json-auth.mjs';

const profile = { id: '0123456789abcdef0123456789abcdef', name: 'FixturePlayer' };
const token = 'fixture-minecraft-token';
const address = 'duckcatmine.example.test';

test('Web Play requires a valid game account on the whitelist and relays only to DuckCatMine', { timeout: 10000 }, async (t) => {
  let whitelisted = false;
  const authorizeProfile = async (uuid) => whitelisted && uuid === profile.id;
  const fetchImpl = async (url) => {
    if (url.endsWith('/entitlements/mcstore')) return Response.json({ items: [{ name: 'game_minecraft' }] });
    if (url.endsWith('/minecraft/profile')) return Response.json(profile);
    throw new Error('Unexpected Minecraft Services request');
  };
  const reservation = createServer();
  reservation.listen(0, '127.0.0.1');
  await once(reservation, 'listening');
  const port = reservation.address().port;
  await new Promise((resolve) => reservation.close(resolve));
  const local = '127.0.0.1:' + port;
  const origin = 'http://' + local;
  const auth = new JsonAccountAuth({ origin, fetchImpl, authorizeProfile });
  const minecraft = createServer((socket) => socket.end());
  minecraft.listen(0, '127.0.0.1');
  await once(minecraft, 'listening');
  t.after(() => minecraft.close());
  let dials = 0;
  const app = createApp({ origin, auth, fetchImpl, gatewayOptions: {
    ...duckCatPlay(address),
    authorizeProfile,
    dial: (target) => {
      assert.deepEqual(target, { hostname: address, port: 25565 });
      dials++;
      return dialTcp('127.0.0.1', minecraft.address().port);
    },
  } });
  app.listen(port, '127.0.0.1');
  await once(app, 'listening');
  t.after(() => app.shutdown());
  const request = (path, options) => fetch(origin + path, options);
  const login = () => request('/mcweb/auth/import', {
    method: 'POST',
    headers: { Origin: origin, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'MSA', profile,
      ygg: { token, exp: Math.floor(Date.now() / 1000) + 3600 },
      entitlement: { ownsMinecraft: true, canPlayMinecraft: true } }),
  });
  const connect = (host, cookie, port = 25565) => new Promise((resolve, reject) => {
    const socket = new WebSocket('ws://' + local + '/mcweb/tcp/?' + new URLSearchParams({ host, port }), {
      headers: { Origin: origin, ...(cookie ? { Cookie: cookie } : {}) },
    });
    socket.once('error', reject);
    socket.once('unexpected-response', (request, response) => {
      response.resume();
      resolve({ status: response.statusCode });
      socket.terminate();
    });
    socket.once('open', () => resolve({ status: 101, socket }));
  });

  assert.equal((await request('/healthz')).status, 200);
  assert.equal((await request('/play/javavm.js')).status, 401);
  assert.equal((await connect(address)).status, 401);
  const denied = await login();
  assert.equal(denied.status, 403);
  assert.equal(denied.headers.get('set-cookie'), null);
  assert.equal((await denied.json()).code, 'not-whitelisted');

  whitelisted = true;
  const admitted = await login();
  assert.equal(admitted.status, 200);
  const cookie = admitted.headers.get('set-cookie').split(';')[0];
  assert.ok(!(await admitted.text()).includes(token));
  const page = await request('/play/', { headers: { Cookie: cookie } });
  assert.equal(page.status, 200);
  assert.match(await page.text(), /'--quickPlayMultiplayer', "duckcatmine\.example\.test:25565"/);
  assert.equal((await connect('other.example.test', cookie)).status, 403);
  assert.equal((await connect(address, cookie, 25575)).status, 403);
  assert.equal(dials, 0);

  const backendConnection = once(minecraft, 'connection');
  const joined = await connect(address, cookie);
  assert.equal(joined.status, 101);
  joined.socket.send(JSON.stringify({ connect: { host: address, port: 25565 } }));
  await backendConnection;
  joined.socket.close();
  assert.equal(dials, 1);

  whitelisted = false;
  assert.equal((await connect(address, cookie)).status, 403);
  assert.equal((await request('/mcweb/auth/session', { headers: { Cookie: cookie } })).status, 401);
  assert.equal(dials, 1);
});
