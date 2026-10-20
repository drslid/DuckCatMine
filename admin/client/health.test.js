import test from 'node:test';
import assert from 'node:assert/strict';
import { serverHealth } from './src/health.js';
const now = Date.now();
const base = { online: true, observedAt: new Date(now).toISOString(), tps: 'TPS from last 1m, 5m, 15m: §a*20.0, 20.0, 20.0', mspt: 'Average: 4.2', metrics: { at: new Date(now).toISOString(), cpu: '120%', cpuCount: 8, memory: '2.4 GiB / 6 GiB' } };
test('Docker 120% means 15% of an eight core VPS and does not indicate game lag', () => {
  const h = serverHealth(base, now); assert.equal(h.level, 'healthy'); assert.equal(h.cpu, 15); assert.equal(h.tps, 20); assert.equal(h.limit, 6 * 1024 ** 3);
});
test('low TPS, memory pressure and stale measurements are distinct', () => {
  assert.equal(serverHealth({ ...base, tps: 'TPS: 12.0' }, now).level, 'critical');
  assert.equal(serverHealth({ ...base, metrics: { ...base.metrics, memory: '5.9 GiB / 6 GiB' } }, now).level, 'warning');
  assert.equal(serverHealth(base, now + 61000).level, 'unknown');
  assert.equal(serverHealth({}, now).level, 'unknown');
  assert.equal(serverHealth({ ...base, online: false }, now).level, 'critical');
});
