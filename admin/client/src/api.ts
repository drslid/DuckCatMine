import { image as demoImage } from './demo';
export const demo = import.meta.env.MODE === 'demo';
let csrf = '';
export function setCsrf(value = '') {
  csrf = value;
}
export async function api(url: string, method = 'GET', data?: any) {
  if (demo) return (await import('./demo')).request(url, method, data);
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: {
      ...(data ? { 'Content-Type': 'application/json' } : {}),
      ...(csrf ? { 'X-CSRF-Token': csrf } : {}),
    },
    body: data ? JSON.stringify(data) : undefined,
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(result.error || `Erreur ${response.status}`);
  return result;
}
export const fold = (s: string) =>
  String(s)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
export const iconUrl = (id: string) =>
  demo ? demoImage(id) : `/icons/${id.replace('minecraft:', '')}.png`;
export const command = (value: string) =>
  api('/api/command', 'POST', { command: value });
export const bytes = (n: number) =>
  n
    ? `${(n / 1024 ** 3).toLocaleString('fr', { maximumFractionDigits: 1 })} Gio`
    : '—';

export const asset = (name: string) =>
  demo ? import.meta.env.BASE_URL + name : '/' + name;
