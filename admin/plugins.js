'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const MAX = 50 * 1024 * 1024;
const API = 'https://api.modrinth.com/v2';
const ID = /^[A-Za-z0-9_-]{1,80}$/;
const USER_AGENT = 'DuckCatMine/0.3 (https://github.com/drslid/DuckCatMine)';

function pluginRoutes({ jobs, requireSession, requireCsrf, body, json, enqueueJob, audit, HttpError, fetcher = fetch }) {
  const cache = new Map();
  let transfers = 0;
  const read = (name, fallback) => {
    try { return JSON.parse(fs.readFileSync(path.join(jobs, name), 'utf8')); }
    catch (e) { if (e.code === 'ENOENT') return fallback; throw e; }
  };
  const fail = (status, message) => { throw new HttpError(status, message); };
  async function catalog(endpoint) {
    const hit = cache.get(endpoint);
    if (hit && hit.until > Date.now()) return hit.promise;
    if (cache.size >= 128) cache.delete(cache.keys().next().value);
    const promise = (async () => {
      const response = await fetcher(API + endpoint, { headers: { 'User-Agent': USER_AGENT }, redirect: 'error', signal: AbortSignal.timeout(15000) });
      if (!response.ok) fail(response.status === 404 ? 404 : 502, 'Catalogue temporairement indisponible. Réessaie dans un instant.');
      const chunks = []; let size = 0;
      for await (const chunk of response.body) {
        size += chunk.length;
        if (size > 2 * 1024 * 1024) fail(502, 'Réponse du catalogue trop volumineuse.');
        chunks.push(chunk);
      }
      return JSON.parse(Buffer.concat(chunks).toString());
    })();
    cache.set(endpoint, { until: Date.now() + 300000, promise });
    try { return await promise; } catch (e) { cache.delete(endpoint); throw e; }
  }
  const gameVersion = () => {
    const version = read('plugins-inventory.json', {}).gameVersion;
    if (!/^[0-9]+(?:\.[0-9]+){1,2}$/.test(version || '')) fail(503, 'Version Minecraft en cours de détection. Réessaie dans quelques secondes.');
    return version;
  };
  const supported = (v, version) => v.game_versions?.includes(version) && v.loaders?.some(l => ['paper', 'spigot', 'bukkit'].includes(l));
  async function transfer(stream, expected, source) {
    if (transfers >= 2) fail(429, 'Deux imports sont déjà en cours. Réessaie dans un instant.');
    const pending = read('plugins-pending.json', []);
    const queued = fs.readdirSync(jobs).filter(n => /\.(upload|request|running)$/.test(n)).length;
    if (pending.length + queued >= 24) fail(409, 'Trop d’ajouts en attente. Active ou annule les ajouts précédents.');
    transfers++;
    const artifact = crypto.randomBytes(16).toString('hex');
    const filename = path.join(jobs, artifact + '.upload');
    let handle, success = false;
    try {
      handle = await fs.promises.open(filename, 'wx', 0o600);
      const hash = crypto.createHash('sha512'); let count = 0, signature = Buffer.alloc(0);
      for await (const value of stream) {
        const chunk = Buffer.from(value); count += chunk.length;
        if (count > MAX) fail(413, '50 Mio maximum par fichier .jar.');
        if (signature.length < 4) signature = Buffer.concat([signature, chunk.subarray(0, 4 - signature.length)]);
        hash.update(chunk);
        await handle.writeFile(chunk);
      }
      if (!count || signature.toString('hex') !== '504b0304') fail(400, 'Le fichier ne ressemble pas à une archive .jar.');
      const sha512 = hash.digest('hex');
      if (expected && sha512 !== expected) fail(400, 'Empreinte du téléchargement incorrecte.');
      await handle.close(); handle = null;
      const id = enqueueJob('plugin-stage', source.username, { artifact, sha512, source: source.label });
      audit('plugin_staged', source.username, source.label);
      success = true;
      return { id };
    } finally {
      await handle?.close();
      if (!success) await fs.promises.unlink(filename).catch(() => {});
      transfers--;
    }
  }
  return async (req, res, url) => {
    if (!url.pathname.startsWith('/api/plugins')) return false;
    const session = requireSession(req);
    if (req.method !== 'GET') requireCsrf(req, session);
    if (req.method === 'GET' && url.pathname === '/api/plugins') {
      const inventory = read('plugins-inventory.json', { installed: [], observedAt: null });
      const pending = read('plugins-pending.json', []);
      const names = new Set([...inventory.installed, ...pending].flatMap(p => [p.name, ...(p.provides || [])]).map(n => n.toLowerCase()));
      json(res, 200, { ...inventory, pending: pending.map(p => ({ ...p, basis: undefined, sha512: undefined,
        missingDependencies: p.dependencies.filter(d => !names.has(d.toLowerCase())) })) });
    } else if (req.method === 'GET' && url.pathname === '/api/plugins/search') {
      const query = (url.searchParams.get('query') || '').trim();
      if (query.length > 80) fail(400, 'Recherche limitée à 80 caractères.');
      const version = gameVersion();
      const facets = JSON.stringify([['project_type:plugin'], ['categories:paper', 'categories:spigot', 'categories:bukkit'], ['versions:' + version]]);
      const data = await catalog('/search?' + new URLSearchParams({ query, facets, limit: '12', index: 'downloads' }));
      json(res, 200, { gameVersion: version, hits: data.hits.map(p => ({ id: p.project_id, slug: p.slug, name: p.title,
        description: p.description, downloads: p.downloads, author: p.author, icon: '/api/plugins/icon/' + p.project_id })) });
    } else if (req.method === 'GET' && /^\/api\/plugins\/project\/[^/]+$/.test(url.pathname)) {
      const id = url.pathname.split('/').at(-1); if (!ID.test(id)) fail(400, 'Projet invalide.');
      const version = gameVersion();
      const [project, versions] = await Promise.all([catalog('/project/' + id), catalog('/project/' + id + '/version?' + new URLSearchParams({ game_versions: JSON.stringify([version]), loaders: JSON.stringify(['paper', 'spigot', 'bukkit']) }))]);
      json(res, 200, { id: project.id, name: project.title, description: project.description,
        url: 'https://modrinth.com/plugin/' + encodeURIComponent(project.slug), gameVersion: version,
        versions: versions.filter(v => supported(v, version) && v.files.some(f => f.filename.endsWith('.jar'))).map(v => ({ id: v.id, name: v.version_number, type: v.version_type,
          dependencies: v.dependencies.filter(d => d.dependency_type === 'required').map(d => d.project_id || d.file_name || d.version_id), date: v.date_published })) });
    } else if (req.method === 'GET' && /^\/api\/plugins\/icon\/[^/]+$/.test(url.pathname)) {
      const id = url.pathname.split('/').at(-1); if (!ID.test(id)) fail(400, 'Projet invalide.');
      const project = await catalog('/project/' + id);
      const icon = new URL(project.icon_url || 'https://invalid.local');
      if (icon.protocol !== 'https:' || icon.hostname !== 'cdn.modrinth.com') fail(404, 'Icône indisponible.');
      const response = await fetcher(icon, { redirect: 'error', signal: AbortSignal.timeout(10000), headers: { 'User-Agent': USER_AGENT } });
      const type = response.headers.get('content-type')?.split(';')[0];
      if (!response.ok || !['image/png', 'image/jpeg', 'image/webp'].includes(type)) fail(404, 'Icône indisponible.');
      const chunks = []; let size = 0;
      for await (const chunk of response.body) { size += chunk.length; if (size > 128 * 1024) fail(413, 'Icône trop volumineuse.'); chunks.push(chunk); }
      res.setHeader('Content-Type', type); res.setHeader('Cache-Control', 'private, max-age=86400'); res.end(Buffer.concat(chunks));
    } else if (req.method === 'POST' && url.pathname === '/api/plugins/upload') {
      const filename = url.searchParams.get('filename') || '';
      if (!/^[^/\\\x00-\x1f]{1,150}\.jar$/i.test(filename)) fail(400, 'Choisis un fichier .jar.');
      if (Number(req.headers['content-length']) > MAX) fail(413, '50 Mio maximum par fichier .jar.');
      json(res, 202, await transfer(req, null, { username: session.username, label: 'Import .jar : ' + filename }));
    } else if (req.method === 'POST' && url.pathname === '/api/plugins/install') {
      const input = await body(req);
      if (!ID.test(input.projectId || '') || !ID.test(input.versionId || '')) fail(400, 'Projet ou version invalide.');
      const version = await catalog('/version/' + input.versionId);
      if (version.project_id !== input.projectId || !supported(version, gameVersion())) fail(400, 'Cette version n’est pas déclarée compatible avec ce serveur.');
      const file = version.files.find(f => f.primary && f.filename.endsWith('.jar')) || version.files.find(f => f.filename.endsWith('.jar'));
      if (!file || file.size > MAX || !/^[a-f0-9]{128}$/.test(file.hashes?.sha512 || '')) fail(400, 'Fichier .jar ou empreinte indisponible.');
      const location = new URL(file.url);
      if (location.protocol !== 'https:' || location.hostname !== 'cdn.modrinth.com' || location.port) fail(400, 'Source de téléchargement non autorisée.');
      if (transfers >= 2) fail(429, 'Import en cours. Réessaie dans un instant.');
      const response = await fetcher(location, { headers: { 'User-Agent': USER_AGENT }, redirect: 'error', signal: AbortSignal.timeout(120000) });
      if (!response.ok) fail(502, 'Téléchargement indisponible.');
      json(res, 202, await transfer(response.body, file.hashes.sha512, { username: session.username, label: 'Modrinth : ' + input.projectId + ' / ' + version.version_number }));
    } else if (req.method === 'POST' && url.pathname === '/api/plugins/cancel') {
      const { artifact } = await body(req);
      if (!/^[a-f0-9]{32}$/.test(artifact || '')) fail(400, 'Identifiant invalide.');
      audit('plugin_cancelled', session.username, artifact);
      json(res, 202, { id: enqueueJob('plugin-cancel', session.username, { artifact }) });
    } else fail(404, 'Action introuvable.');
    return true;
  };
}
module.exports = { pluginRoutes };
