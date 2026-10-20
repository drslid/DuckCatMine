const strip = (text) =>
  String(text || '')
    .replace(/\x1b\[[0-9;]*m/g, '')
    .replace(/§[0-9a-fk-orx]/gi, '');
const numeric = (text) => {
  const values = strip(text)
    .split(':')
    .at(-1)
    .match(/\d+(?:\.\d+)?/g);
  return values ? Number(values[0]) : null;
};
function memory(text) {
  const match = String(text || '')
    .trim()
    .match(/^([\d.]+)\s*([KMGT]?i?B)/i);
  if (!match) return null;
  const power = {
    b: 0,
    kib: 1,
    mib: 2,
    gib: 3,
    tib: 4,
    kb: 1,
    mb: 2,
    gb: 3,
    tb: 4,
  }[match[2].toLowerCase()];
  return (
    Number(match[1]) *
    (match[2].toLowerCase().includes('i') ? 1024 : 1000) ** power
  );
}
export const compactMemory = (n) =>
  n == null
    ? '—'
    : (n / 1024 ** 3).toLocaleString('fr', { maximumFractionDigits: 1 });
export function serverHealth(status, now = Date.now()) {
  const m = status.metrics || {};
  const cpu = Number.isFinite(m.hostCpuPercent)
    ? m.hostCpuPercent
    : m.cpuCount
      ? parseFloat(m.cpu) / m.cpuCount
      : null;
  const used = m.memoryBytes ?? memory(m.memory?.split('/')[0]);
  const limit = m.memoryLimitBytes ?? memory(m.memory?.split('/')[1]);
  const tps = numeric(status.tps),
    mspt = numeric(status.mspt);
  const result = {
    level: 'unknown',
    label: 'Mesure en cours',
    detail: 'Les mesures arrivent automatiquement.',
    cpu,
    used,
    limit,
    tps,
    mspt,
  };
  if (status.observedAt && !status.online)
    return {
      ...result,
      level: 'critical',
      label: 'Serveur indisponible',
      detail: 'La connexion à Minecraft a échoué. Ouvre le diagnostic.',
    };
  const observed = Date.parse(status.observedAt || '');
  if (!status.online || !Number.isFinite(observed)) return result;
  if (
    now - observed > 30000 ||
    !m.at ||
    now - Date.parse(m.at) > 60000 ||
    !Number.isFinite(Date.parse(m.at))
  )
    return {
      ...result,
      label: 'Mesures anciennes',
      detail:
        'Le suivi doit se reconnecter avant de confirmer l’état du serveur.',
      cpu: null,
      used: null,
      limit: null,
    };
  if (tps == null) return result;
  const reasons = [];
  let level = 'healthy';
  if (tps < 15 || mspt >= 50) {
    level = 'critical';
    reasons.push('La partie ralentit');
  } else if (tps < 18 || mspt >= 45) {
    level = 'warning';
    reasons.push('La fluidité baisse');
  }
  if (cpu >= 85) {
    if (level !== 'critical') level = 'warning';
    reasons.push('CPU du jeu élevé');
  }
  if (limit > 0 && used / limit >= 0.9) {
    if (level !== 'critical') level = 'warning';
    reasons.push('Mémoire du jeu proche de sa limite');
  }
  if (m.host?.MemTotal > 0 && m.host.MemAvailable / m.host.MemTotal < 0.08) {
    if (level !== 'critical') level = 'warning';
    reasons.push('Peu de mémoire libre sur le VPS');
  }
  if (m.diskTotal > 0 && m.diskFree / m.diskTotal < 0.1) {
    if (m.diskFree / m.diskTotal < 0.05) level = 'critical';
    else if (level !== 'critical') level = 'warning';
    reasons.push('Disque presque plein');
  }
  return {
    ...result,
    level,
    label:
      level === 'healthy'
        ? 'Tout va bien'
        : level === 'critical'
          ? 'À vérifier'
          : 'À surveiller',
    detail: reasons.length
      ? reasons.join(' · ')
      : `${tps.toLocaleString('fr')} TPS · Le jeu tourne normalement.`,
  };
}
