import { publicTcpTarget } from '../relay-service/public-target.mjs';
import { dialTcp } from '../relay-service/tcp.mjs';
import { JsonAuthError } from './json-auth.mjs';

export function duckCatPlay(address) {
  const match = /^([A-Za-z0-9.-]+)(?::([0-9]+))?$/.exec(address || '');
  const allowedTarget = match && publicTcpTarget(match[1], match[2] || '25565');
  if (!allowedTarget) throw new Error('MC_ADDRESS must contain a public Java hostname and optional port.');
  return {
    allowedTarget,
    authorizeProfile,
    // The browser address is a label for this one backend. Never resolve a
    // user-supplied destination through the Docker network.
    dial: () => dialTcp('minecraft', 25565),
  };
}

async function authorizeProfile(uuid) {
  if (!/^[0-9a-f]{32}$/.test(uuid)) return false;
  const response = await fetch('http://panel:3001/players/' + uuid, {
    redirect: 'error',
    signal: AbortSignal.timeout(2000),
  }).catch(() => null);
  if (response?.status === 204) return true;
  if (response?.status === 403) return false;
  throw new JsonAuthError('whitelist-unavailable', 503);
}
