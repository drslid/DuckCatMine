// This adapter runs entirely in the browser. It never contacts a game server.
const meta = {
  diamond: ['Diamant', 'other'],
  elytra: ['Élytres', 'tools'],
  golden_apple: ['Pomme dorée', 'food'],
  netherite_sword: ['Épée en netherite', 'tools'],
  oak_planks: ['Planches de chêne', 'blocks'],
  torch: ['Torche', 'decor'],
  bread: ['Pain', 'food'],
  rail: ['Rails', 'transport'],
  minecart: ['Wagonnet', 'transport'],
  stone: ['Pierre', 'blocks'],
  glass: ['Verre', 'blocks'],
  oak_log: ['Bûche de chêne', 'blocks'],
  emerald: ['Émeraude', 'other'],
  iron_pickaxe: ['Pioche en fer', 'tools'],
  diamond_boots: ['Bottes en diamant', 'tools'],
  redstone: ['Redstone', 'other'],
  apple: ['Pomme', 'food'],
  lantern: ['Lanterne', 'decor'],
  chest: ['Coffre', 'decor'],
  wolf_spawn_egg: ['Loup', 'creatures'],
  horse_spawn_egg: ['Cheval', 'creatures'],
  villager_spawn_egg: ['Villageois', 'creatures'],
  cat_spawn_egg: ['Chat', 'creatures'],
  zombie_spawn_egg: ['Zombie', 'creatures'],
  fox_spawn_egg: ['Renard', 'creatures'],
  cow_spawn_egg: ['Vache', 'creatures'],
  bee_spawn_egg: ['Abeille', 'creatures'],
};
const catalog = {
  items: Object.keys(meta).map((k) => 'minecraft:' + k),
  meta: Object.fromEntries(
    Object.entries(meta).map(([k, [name, category]]) => [
      'minecraft:' + k,
      { name, category, icon: true },
    ]),
  ),
};
const messages = [
  {
    id: 'demo:1',
    at: '18:42',
    author: 'Alex',
    message: 'Le village prend forme !',
  },
  {
    id: 'demo:2',
    at: '18:43',
    author: 'Sam',
    message: 'Je ramène du bois pour la prochaine tour 🌲',
  },
  {
    id: 'demo:3',
    at: '18:44',
    author: 'Alex',
    message: 'On se retrouve près du spawn ?',
  },
];
const state: any = {
  mapPolicy: 'idle',
  online: true,
  address: 'votre-monde.duckdns.org',
  players: 'There are 2 of a max of 10 players online: Alex, Sam',
  tps: 'TPS from last 1m: 20.0',
  mspt: 'Average: 4.2',
  whitelist: [{ name: 'Alex' }, { name: 'Sam' }, { name: 'Robin' }],
  ops: [{ name: 'Alex' }, { name: 'Sam' }],
  properties: {
    motd: 'Notre petit monde',
    'max-players': '10',
    pvp: 'false',
    'view-distance': '8',
    'simulation-distance': '6',
  },
  gamerules: {
    keep_inventory: 'true',
    advance_time: 'true',
    players_sleeping_percentage: '50',
    mob_griefing: 'false',
    spawn_mobs: 'true',
  },
  pending: {},
  backups: [
    {
      name: 'world-20261020-180000.tar.gz',
      at: '2026-10-20T18:00:00Z',
      size: 384 * 1024 ** 2,
    },
    {
      name: 'world-20261019-180000.tar.gz',
      at: '2026-10-19T18:00:00Z',
      size: 360 * 1024 ** 2,
    },
  ],
  metrics: {
    cpu: '18.4%',
    memory: '2.4 GiB / 4 GiB',
    host: { MemAvailable: 5.6 * 1024 ** 3 },
    services: [
      { name: 'duckcatmine-minecraft-1', memoryBytes: 2.4 * 1024 ** 3 },
      { name: 'duckcatmine-admin-panel-1', memoryBytes: 42 * 1024 ** 2 },
    ],
  },
  logs: '[18:44:02] Server ready. 2 players online.',
  plugins: 'DuckCatWelcome, DuckCatTowers, BlueMap, SkinsRestorer, mCraft',
};
let messageId = 3;
const inventories: any = {};
const statusByPlayer: any = {};
const towers = [
  {
    id: 'medieval',
    name: 'Bastion médiéval',
    style: 'Pierre et bois',
    width: 3,
    height: 6,
    length: 3,
    range: 32,
    defensive: true,
  },
  {
    id: 'fantasy',
    name: 'Tour arcanique',
    style: 'Améthyste et purpur',
    width: 3,
    height: 6,
    length: 3,
    range: 32,
    defensive: true,
  },
  {
    id: 'modern',
    name: 'Poste moderne',
    style: 'Fer et cuivre',
    width: 3,
    height: 6,
    length: 3,
    range: 32,
    defensive: true,
  },
];
export async function request(url: string, method = 'GET', data: any = {}) {
  const u = new URL(url, 'https://demo.invalid');
  if (u.pathname === '/api/me' || u.pathname === '/api/login')
    return { username: 'Alex', csrf: 'demo' };
  if (u.pathname === '/api/status')
    return structuredClone({ ...state, observedAt: new Date().toISOString() });
  if (u.pathname === '/api/chat') return { messages: [...messages] };
  if (u.pathname === '/api/chat/send') {
    messages.push({
      id: `demo:${++messageId}`,
      at: new Date().toLocaleTimeString('fr', {
        hour: '2-digit',
        minute: '2-digit',
      }),
      author: 'Alex',
      message: data.message,
      ...{ source: 'web' },
    });
    return { ok: true };
  }
  if (u.pathname === '/api/items/catalog') return catalog;
  if (u.pathname === '/api/skins/catalog')
    return {
      source: 'https://craftdex.net',
      skins: Array.from({ length: 12 }, (_, i) => ({
        id: `demo${i}`,
        hash: `demo${i}`,
        name:
          [
            u.searchParams.get('query') || 'Explorateur',
            'Bâtisseur',
            'Aventurier',
            'Gardien',
          ][i % 4] +
          ' ' +
          (i + 1),
        source: 'craftdex',
      })),
    };
  if (/\/api\/players\/[^/]+\/details/.test(u.pathname)) {
    const name = u.pathname.split('/')[3];
    statusByPlayer[name] ||= {
      name,
      health: 20,
      maxHealth: 20,
      food: 18,
      level: 12,
      gamemode: 'survival',
      world: 'world',
      x: 40,
      y: 65,
      z: -80,
      op: state.ops.some((p) => p.name === name),
      selected: 0,
    };
    const make = (section, count) =>
      Array.from({ length: count }, (_, i) => ({
        slot: `${section}:${section === 'equipment' ? ['head', 'chest', 'legs', 'feet', 'offhand'][i] : i}`,
        rev: '1',
        id: i < 6 && section === 'main' ? catalog.items[i] : '',
        name:
          i < 6 && section === 'main'
            ? catalog.meta[catalog.items[i]].name
            : '',
        amount: i < 6 ? i * 3 + 1 : 0,
      }));
    inventories[name] ||= {
      main: make('main', 36),
      equipment: make('equipment', 5),
      ender: make('ender', 27),
    };
    return structuredClone({
      status: statusByPlayer[name],
      inventory: inventories[name],
    });
  }
  if (u.pathname === '/api/players/live-action') {
    const s = statusByPlayer[data.player];
    if (data.operation === 'heal') s.health = 20;
    if (data.operation === 'feed') s.food = 20;
    if (data.operation === 'xp') s.level += data.levels;
    if (data.slot) {
      const group = data.slot.split(':')[0],
        item = inventories[data.player][group].find(
          (i) => i.slot === data.slot,
        );
      if (data.operation === 'set')
        Object.assign(item, {
          id: data.item,
          amount: data.count,
          name: catalog.meta[data.item]?.name,
        });
      if (data.operation === 'clear')
        Object.assign(item, { id: '', amount: 0, name: '' });
      if (data.operation === 'amount') item.amount = data.count;
      if (data.operation === 'swap') {
        const target = inventories[data.player][
          data.targetSlot.split(':')[0]
        ].find((i) => i.slot === data.targetSlot);
        const a = { id: item.id, amount: item.amount, name: item.name };
        Object.assign(item, {
          id: target.id,
          amount: target.amount,
          name: target.name,
        });
        Object.assign(target, a);
      }
    }
    return { ok: true };
  }
  if (u.pathname === '/api/command') {
    const [cmd, p1, p2] = data.command.split(' ');
    if (cmd === 'gamemode' && statusByPlayer[p2])
      statusByPlayer[p2].gamemode = p1;
    if (cmd === 'op' && !state.ops.some((p) => p.name === p1))
      state.ops.push({ name: p1 });
    if (cmd === 'deop') state.ops = state.ops.filter((p) => p.name !== p1);
    if (
      cmd === 'whitelist' &&
      p1 === 'add' &&
      !state.whitelist.some((p) => p.name === p2)
    )
      state.whitelist.push({ name: p2 });
    if (cmd === 'gamerule') state.gamerules[p1] = p2;
    return { result: 'Modification appliquée dans cette démo.' };
  }
  if (u.pathname === '/api/towers/catalog')
    return { active: true, towers: [...towers] };
  if (u.pathname === '/api/towers/check')
    return { result: 'Terrain libre. Prêt à construire.' };
  if (u.pathname === '/api/towers/place')
    return { result: 'Construction posée dans la démo.' };
  if (u.pathname === '/api/mobs/spawn') return { spawned: data.count };
  if (u.pathname === '/api/towers/upload') {
    const tower = {
      id: 'personal',
      name: data.filename,
      width: 12,
      height: 8,
      length: 12,
      preview: Array(16).fill('SSSSSSSSSSSSSSSS').join('/'),
    };
    towers.push(tower as any);
    return { tower };
  }
  if (u.pathname === '/api/settings') {
    state.pending = data;
    return { pending: data };
  }
  if (u.pathname === '/api/map/policy') {
    state.mapPolicy = data.policy;
    return { id: 'demo-policy' };
  }
  if (u.pathname === '/api/jobs') return { id: 'demo-job' };
  if (u.pathname.startsWith('/api/jobs/'))
    return { status: 'done', ok: true, message: 'Opération simulée terminée.' };
  return { ok: true, result: 'Action simulée.' };
}
export function image(id: string) {
  const name = id.replace('minecraft:', '');
  const egg = name.endsWith('_egg');
  const green = name.includes('emerald');
  const stone = name.includes('stone') || name.includes('iron');
  const color = green
    ? '#6bc58d'
    : stone
      ? '#a9b9be'
      : name.includes('diamond')
        ? '#7fd6db'
        : name.includes('apple')
          ? '#dbaa64'
          : '#bd9b65';
  const art = egg
    ? `<path d="M10 3h12l7 12v11l-6 6H9l-6-6V15z" fill="${color}"/><path d="M10 9h6v6h-6zM20 19h6v6h-6z" fill="#758a62"/>`
    : name.includes('diamond') || green
      ? `<path d="M8 3h18l7 10-16 20L1 13z" fill="${color}"/><path d="M8 3 1 13h32M8 3l9 30 9-30" fill="none" stroke="#e6f7ec" stroke-width="2"/>`
      : `<path d="m2 10 15-8 15 8v17l-15 8-15-8z" fill="${color}"/><path d="m2 10 15 8 15-8M17 18v17" fill="none" stroke="#493e34" stroke-width="2"/>`;
  return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 34 36">${art}</svg>`)}`;
}
