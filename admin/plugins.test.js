'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { Readable } = require('node:stream');
const { pluginRoutes } = require('./plugins');

test('plugin routes require auth and CSRF, filter compatible projects and stream verified files to the queue', async () => {
  const jobs = fs.mkdtempSync(path.join(os.tmpdir(), 'dc-plugins-'));
  const requests = [], calls = [];
  const payload = Buffer.from('504b030401020304', 'hex');
  const hash = crypto.createHash('sha512').update(payload).digest('hex');
  class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }
  const handle = pluginRoutes({ jobs, HttpError, json: (res, status, value) => Object.assign(res, { status, value }),
    requireSession: req => { if (!req.auth) throw new HttpError(401, 'auth'); return { username: 'Alex' }; },
    requireCsrf: req => { if (!req.csrf) throw new HttpError(403, 'csrf'); },
    body: async req => req.input, enqueueJob: (type, user, extra) => { requests.push({ type, user, ...extra }); return 'job'; }, audit: () => {},
    fetcher: async location => {
      const url = String(location); calls.push(url);
      if (url.includes('/search?')) return Response.json({ hits: [{ project_id: 'example', slug: 'example', title: 'Example', description: 'description', downloads: 12, author: 'Author' }] });
      if (url.includes('/version/')) return Response.json({ project_id: 'example', game_versions: ['26.3'], loaders: ['paper'], version_number: '1.0', files: [{ primary: true, filename: 'Example.jar', size: payload.length, url: 'https://cdn.modrinth.com/data/example/Example.jar', hashes: { sha512: hash } }] });
      if (url.startsWith('https://cdn.modrinth.com/')) return new Response(payload);
      throw new Error('Unexpected fetch: ' + url);
    },
  });
  async function request(endpoint, { method = 'GET', auth = true, csrf = true, input, bytes = Buffer.alloc(0), headers = {} } = {}) {
    const req = Readable.from([bytes]); Object.assign(req, { method, auth, csrf, input, headers });
    const res = {}; await handle(req, res, new URL(endpoint, 'https://local.invalid')); return res;
  }
  try {
    fs.writeFileSync(path.join(jobs, 'plugins-inventory.json'), JSON.stringify({ installed: [], gameVersion: '26.3' }));
    await assert.rejects(request('/api/plugins', { auth: false }), e => e.status === 401);
    await assert.rejects(request('/api/plugins/upload?filename=Example.jar', { method: 'POST', csrf: false }), e => e.status === 403);
    await assert.rejects(request('/api/plugins/upload?filename=../bad.jar', { method: 'POST' }), e => e.status === 400);
    await assert.rejects(request('/api/plugins/upload?filename=Example.jar', { method: 'POST', bytes: Buffer.from('not zip') }), e => e.status === 400);
    assert.equal(fs.readdirSync(jobs).filter(n => n.endsWith('.upload')).length, 0);
    const search = await request('/api/plugins/search?query=permissions'); assert.equal(search.value.hits[0].name, 'Example');
    const params = new URL(calls[0]).searchParams; assert.ok(params.get('facets').includes('versions:26.3'));
    await request('/api/plugins/search?query=permissions'); assert.equal(calls.length, 1, 'identical searches use the shared cache');
    const install = await request('/api/plugins/install', { method: 'POST', input: { projectId: 'example', versionId: 'version' } });
    assert.equal(install.status, 202); assert.equal(requests[0].type, 'plugin-stage'); assert.equal(requests[0].sha512, hash);
    assert.deepEqual(fs.readFileSync(path.join(jobs, requests[0].artifact + '.upload')), payload);
    await assert.rejects(request('/api/plugins/install', { method: 'POST', input: { projectId: 'other', versionId: 'version' } }), e => e.status === 400);
    await request('/api/plugins/upload?filename=Example.jar', { method: 'POST', bytes: payload });
    assert.equal(requests.length, 2);
  } finally { fs.rmSync(jobs, { recursive: true, force: true }); }
});
