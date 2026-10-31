'use strict';

// Keep command construction separate from HTTP and RCON. Every token is validated here.
const SETTINGS = {
  'infection-seconds': ['integer', 10, 600], 'bite-multiplier': ['decimal', 0, 3],
  'medical-boost': ['decimal', .25, 10], 'bag-seconds': ['integer', 30, 3600],
  'protection-seconds': ['integer', 0, 120], spawning: ['boolean'],
  density: ['decimal', .25, 5], 'per-tick': ['integer', 1, 5],
  'global-cap': ['integer', 0, 400], 'night-bonus': ['integer', 0, 16],
  'threat-per-hour': ['decimal', 0, 100], fauna: ['boolean'],
  'fauna-share': ['decimal', 0, .6], 'fauna-chance': ['decimal', 0, 1],
  vehicles: ['boolean'], 'city-chance': ['decimal', 0, 1],
  'leave-seconds': ['integer', 0, 120], intro: ['boolean'], 'team-size': ['integer', 2, 16],
};

function makeApocalypseCommands(HttpError) {
  const fail = (message, status = 400) => { throw new HttpError(status, message); };
  const number = (value, min, max, integer = false) => {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max || (integer && !Number.isInteger(value)))
      fail(`Valeur ${integer ? 'entière ' : ''}entre ${min} et ${max} attendue.`);
    return value;
  };
  const confirmation = (input, expected) => {
    if (input.confirmation !== expected) fail(`Confirmation requise : ${expected}.`);
  };
  const target = (name, status, outside = false) => {
    if (typeof name !== 'string' || !/^[A-Za-z0-9_]{3,16}$/.test(name)) fail('Pseudo invalide.');
    const survivor = status.survivors?.find(p => p.name === name);
    if (!survivor) fail('Ce survivant n’est plus présent dans Ground Zero.', 409);
    if (outside && survivor.inRefuge) fail('Ce survivant est à l’abri dans le refuge.', 409);
    return name;
  };
  function command(input, status) {
    if (!input || typeof input !== 'object' || Array.isArray(input)) fail('Action invalide.');
    const action = input.action, value = input.value;
    // Validate the action before the availability check, so unknown tokens never reach RCON.
    const simple = ['status', 'settings', 'enable', 'disable', 'selftest', 'reload'];
    const known = [...simple, 'pvp', 'destruction', 'threat', 'horde', 'map', 'set', 'preset', 'give', 'evacuate', 'upgrade', 'lives-reset', 'world-create', 'world-bind', 'world-recreate', 'teleport', 'departure-start', 'departure-cancel'];
    if (!known.includes(action)) fail('Action inconnue.');
    if (!status?.installed || status.stale) fail('Ground Zero ne répond pas. Attends la fin du démarrage.', 409);
    const result = (suffix, detail = '') => ({ command: `apocalypse admin ${suffix}`, detail });
    if (action.startsWith('departure-')) {
      if (!status.capabilities?.includes('departure')) fail('Mets le plugin Ground Zero à jour pour gérer les départs groupés.', 409);
      if (action === 'departure-start' && (!status.departure?.members?.length || status.departure.phase !== 'waiting'))
        fail('Aucun groupe en attente de départ.', 409);
      return result(`departure ${action === 'departure-start' ? 'start' : 'cancel'}`, 'départ groupé');
    }
    if (action === 'teleport') {
      if (!status.capabilities?.includes('teleport')) fail('Mets le plugin Ground Zero à jour pour déplacer les survivants.', 409);
      const player = target(value?.player, status), beside = target(value?.target, status);
      if (player === beside) fail('Choisis un autre survivant.');
      return result(`teleport ${player} ${beside}`, `${player} -> ${beside}`);
    }
    if (simple.includes(action)) return result(action);
    if (action === 'pvp' || action === 'destruction') {
      if (typeof value !== 'boolean') fail('Valeur oui/non attendue.');
      return result(`${action} ${value ? 'on' : 'off'}`, String(value));
    }
    if (action === 'threat') {
      const level = number(value, 1, 50).toFixed(1);
      return result(`threat ${level}`, level);
    }
    if (action === 'horde' || action === 'evacuate') {
      const player = target(value, status, action === 'horde');
      return result(`${action} ${player}`, player);
    }
    if (action === 'map') {
      const radius = number(typeof value === 'object' && value ? value.radius : value, 32, 1536, true);
      const slice = value && typeof value === 'object' && value.slice != null ? number(value.slice, -2048, 2048, true) : null;
      return result(`map ${radius}${slice == null ? '' : ` ${slice}`}`, `${radius}${slice == null ? '' : ` y=${slice}`}`);
    }
    if (action === 'set') {
      if (typeof value?.key !== 'string') fail('Réglage inconnu.');
      const setting = SETTINGS[value?.key];
      if (!Object.hasOwn(SETTINGS, value?.key) || !setting) fail('Réglage inconnu.');
      let text;
      if (setting[0] === 'boolean') {
        if (typeof value.value !== 'boolean') fail('Valeur oui/non attendue.');
        text = value.value ? 'true' : 'false';
      } else text = String(number(value.value, setting[1], setting[2], setting[0] === 'integer'));
      return result(`set ${value.key} ${text}`, `${value.key}=${text}`);
    }
    if (action === 'preset') {
      if (!['relaxed', 'normal', 'nightmare'].includes(value)) fail('Préréglage inconnu.');
      return result(`preset ${value}`, value);
    }
    if (action === 'give') {
      const player = target(value?.player, status);
      if (typeof value.item !== 'string' || !/^[a-z][a-z0-9_]{0,47}$/.test(value.item)
          || !status.items?.some(item => item.id === value.item)) fail('Choisis un objet du catalogue Ground Zero.');
      const count = number(value.count, 1, 64, true);
      return result(`give ${player} ${value.item} ${count}`, `${player} ${value.item} x${count}`);
    }
    if (action === 'upgrade') {
      const radius = number(value, 16, 512, true);
      confirmation(input, 'ACTUALISER');
      return result(`upgrade ${radius}`, `refuge rayon=${radius}`);
    }
    if (action === 'lives-reset') {
      confirmation(input, 'RECOMMENCER');
      if (status.survivors?.length) fail('Fais d’abord sortir tous les survivants de Ground Zero.', 409);
      return result('lives reset confirm', 'nouvelle partie');
    }
    if (action === 'world-create') {
      confirmation(input, 'CREER');
      if (status.world) fail('Un monde est déjà associé à Ground Zero.', 409);
      return result('world create');
    }
    if (action === 'world-bind') {
      const world = value;
      if (typeof world !== 'string' || !/^[A-Za-z0-9_-]{1,48}$/.test(world)
          || ['world', 'world_nether', 'world_the_nether', 'world_the_end', 'overworld', 'the_nether', 'the_end'].includes(world.toLowerCase()))
        fail('Choisis un monde dédié chargé, distinct des mondes de survie habituels.');
      confirmation(input, world);
      if (status.world) fail('Un monde est déjà associé à Ground Zero.', 409);
      return result(`world bind ${world} confirm`, world);
    }
    confirmation(input, 'RECREER');
    if (status.world || !status.problem) fail('La recréation est réservée à un monde associé devenu absent.', 409);
    return result('world recreate confirm');
  }
  return { command };
}

function failure(output) {
  const lines = String(output || '').replace(/§./g, '').replace(/☣\s*/g, '').trim().split('\n').map(line => line.trim());
  const explicit = lines.find(line => line.includes('DCAPO_ERROR:'));
  if (explicit) return explicit.slice(explicit.indexOf('DCAPO_ERROR:') + 'DCAPO_ERROR:'.length).trim();
  // Compatibility with older plugins, whose console replies did not carry a machine-readable marker.
  return lines.find(line => /unknown (?:or incomplete )?command|incorrect argument|réservé aux administrateurs|^ÉCHEC|^Configuration refusée|^Réglage inconnu|^Valeur invalide|^Préréglage inconnu|^Le joueur doit|^Sortie impossible|^Un monde est déjà|^Aucun monde associé|^Monde non chargé|^Ce monde ne peut|^Le monde .* existe|^Création du monde impossible|^Chargement du monde .* impossible|^.*est dans Ground Zero : attends|^.*impossible.*(sauvegarde|base de données)/i.test(line)) || null;
}

module.exports = { makeApocalypseCommands, failure, SETTINGS };
