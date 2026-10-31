// This adapter runs entirely in the browser. It never contacts a game server.
import { gzSettings, gzCatalog, gzPresets, gzItems } from './ground-zero-demo';
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
  gameVersion: '26.3',
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
    cpuCount: 4,
    memoryBytes: 2.4 * 1024 ** 3,
    memoryLimitBytes: 4 * 1024 ** 3,
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
const demoPlugins = [
  {
    name: 'DuckCatWelcome',
    version: '2.0.0',
    filename: 'DuckCatWelcome.jar',
    core: true,
  },
  {
    name: 'DuckCatTowers',
    version: '2.0.0',
    filename: 'DuckCatTowers.jar',
    core: true,
  },
  { name: 'BlueMap', version: '5.29', filename: 'BlueMap.jar', core: true },
  {
    name: 'WorldEdit',
    version: '7.4.6',
    filename: 'WorldEdit.jar',
    core: true,
  },
  {
    name: 'SkinsRestorer',
    version: '15.12.6',
    filename: 'SkinsRestorer.jar',
    core: true,
  },
];
let demoPending: any[] = [];
const demoCatalog = [
  {
    id: 'permissions',
    name: 'Permissions du village',
    description:
      'Des rôles simples pour les joueurs, les bâtisseurs et les administrateurs.',
    author: 'Exemple fictif',
    downloads: 24500,
  },
  {
    id: 'quests',
    name: 'Les petites quêtes',
    description:
      'Quelques objectifs à partager pour animer les soirées de jeu.',
    author: 'Exemple fictif',
    downloads: 18200,
  },
  {
    id: 'protect',
    name: 'Protection des constructions',
    description: 'Protéger sa maison et inviter ses amis à y construire.',
    author: 'Exemple fictif',
    downloads: 46700,
  },
];
const apocalypseRules = { pvp: false, destruction: true, globalCap: 60 };
let apocalypseThreat = 3.4;
let apocalypseEnabled = true;
const evacuated = new Set<string>();
const gzEvents = [
  { at: Date.now() - 80000, kind: 'join', text: 'Sam a rejoint Ground Zero' },
  {
    at: Date.now() - 180000,
    kind: 'horde',
    text: 'Une horde approche du centre-ville',
  },
];
let gzDeparture = {
  members: ['Noah', 'Lea'],
  phase: 'waiting',
  target: 'point commun dans la ville',
};
let gzDepartureEnd = 0;
const gzRelocated = new Map<string, { x: number; z: number }>();
function demoApocalypse() {
  if (gzDepartureEnd && Date.now() >= gzDepartureEnd) {
    gzDeparture = { ...gzDeparture, members: [], phase: 'waiting' };
    gzDepartureEnd = 0;
  }
  return {
    installed: true,
    stale: false,
    ageSeconds: 2,
    version: 3,
    capabilities: [
      'departure',
      'teleport',
      'mouse-driving',
      'infected-map',
      'map-teleport',
      'radar',
      'city-renovation',
    ],
    departure: gzDeparture,
    generatedAt: Date.now(),
    running: true,
    enabled: apocalypseEnabled,
    world: 'ground_zero',
    epoch: 2,
    database: true,
    time: { day: 4, night: false },
    threat: {
      level: apocalypseThreat,
      phase: 'pressure',
      phaseLabel: 'pression',
      secondsLeft: 74,
      hordes: 3,
    },
    rules: apocalypseRules,
    infected: { walker: 9, pursuer: 4, runner: 2, howler: 1, infected_wolf: 1 },
    archetypeLabels: {
      walker: 'Errant',
      pursuer: 'Poursuivant',
      runner: 'Sprinteur',
      howler: 'Hurleur',
      infected_wolf: 'Loup infecté',
    },
    infectedTotal: 17,
    infectedPositions: Array.from({ length: 17 }, (_, i) => ({
      id: `00000000-0000-4000-8000-${String(i).padStart(12, '0')}`,
      type: i === 0 ? 'abomination' : i < 4 ? 'runner' : 'walker',
      x: 110 + (i % 5) * 12 + Math.round(Math.sin(Date.now() / 5000 + i) * 3),
      y: 65,
      z: -60 + Math.floor(i / 5) * 14,
    })),
    incubatingAnimals: 1,
    activeInfections: 1,
    activeBags: 1,
    damagedBlocks: 3,
    survivors: [
      {
        name: 'Alex',
        y: 65,
        yaw: 0,
        x: 120,
        z: -64,
        sector: 'Centre-ville',
        inRefuge: false,
        health: 14,
        infectionSeconds: null,
        team: 'Corbeaux',
        points: 340,
        lifeSeconds: 2710,
      },
      {
        name: 'Sam',
        y: 65,
        yaw: -90,
        x: 126,
        z: -58,
        sector: 'Centre-ville',
        inRefuge: false,
        health: 6.5,
        infectionSeconds: 38,
        team: 'Corbeaux',
        points: 95,
        lifeSeconds: 980,
      },
      {
        name: 'Mia',
        y: 65,
        yaw: 0,
        x: 2,
        z: 4,
        sector: 'Refuge',
        inRefuge: true,
        health: 20,
        infectionSeconds: null,
        team: null,
        points: 0,
        lifeSeconds: 120,
      },
    ]
      .map((s) => ({ ...s, ...gzRelocated.get(s.name) }))
      .filter((s) => apocalypseEnabled && !evacuated.has(s.name)),
    teams: [{ name: 'Corbeaux', members: ['Alex', 'Sam'] }],
    classicProfilesHeld: 0,
    refuge: { x: 0, z: 0, radius: 12 },
    borderSize: 3072,
    map: { minX: -320, minZ: -320, size: 640 },
    mapRenderedAt: Date.now() - 3600000,
    settings: gzSettings,
    settingsCatalog: gzCatalog,
    presets: gzPresets,
    items: gzItems,
    events: gzEvents,
    leaderboard: [
      { name: 'Alex', points: 340 },
      { name: 'Sam', points: 95 },
      { name: 'Mia', points: 0 },
    ],
  };
}
export async function request(url: string, method = 'GET', data: any = {}) {
  const u = new URL(url, 'https://demo.invalid');
  if (u.pathname === '/api/me' || u.pathname === '/api/login')
    return { username: 'Alex', csrf: 'demo' };
  state.metrics.at = new Date().toISOString();
  if (u.pathname === '/api/plugins')
    return {
      gameVersion: '26.3',
      observedAt: Date.now(),
      installed: demoPlugins,
      pending: demoPending,
    };
  if (u.pathname === '/api/plugins/search')
    return {
      gameVersion: '26.3',
      hits: demoCatalog.filter((p) =>
        (p.name + ' ' + p.description)
          .toLowerCase()
          .includes((u.searchParams.get('query') || '').toLowerCase()),
      ),
    };
  if (u.pathname.startsWith('/api/plugins/project/')) {
    const p =
      demoCatalog.find((p) => p.id === u.pathname.split('/').at(-1)) ||
      demoCatalog[0];
    return {
      ...p,
      gameVersion: '26.3',
      url: 'https://modrinth.com/plugins',
      versions: [
        {
          id: p.id + '-v',
          name: '1.0.0',
          type: 'release',
          date: '2026-10-01',
          dependencies: [],
        },
      ],
    };
  }
  if (
    u.pathname === '/api/plugins/install' ||
    u.pathname === '/api/plugins/upload'
  ) {
    const p = demoCatalog.find((p) => p.id === data.projectId);
    demoPending.push({
      id: 'demo-' + Date.now(),
      name: p?.name || data.filename.replace(/\.jar$/i, ''),
      version: '1.0.0',
      source: 'Démonstration',
      missingDependencies: [],
    });
    return { id: 'demo-job' };
  }
  if (u.pathname === '/api/plugins/cancel') {
    demoPending = demoPending.filter((p) => p.id !== data.artifact);
    return { id: 'demo-job' };
  }
  if (u.pathname === '/api/apocalypse')
    return structuredClone(demoApocalypse());
  if (u.pathname === '/api/apocalypse/action') {
    if (data.action === 'departure-start') {
      gzDeparture.phase = 'countdown';
      gzDepartureEnd = Date.now() + 5000;
      return { result: 'Démonstration : départ groupé dans 5 secondes.' };
    }
    if (data.action === 'departure-cancel') {
      gzDeparture.members = [];
      gzDeparture.phase = 'waiting';
      gzDepartureEnd = 0;
    }
    if (data.action === 'teleport-point') {
      gzRelocated.set('Alex', { x: data.value.x, z: data.value.z });
      return {
        result:
          'Démonstration : Alex a rejoint le point choisi. Aucun serveur contacté.',
      };
    }
    if (data.action === 'teleport') {
      const at = demoApocalypse().survivors.find(
        (s) => s.name === data.value.target,
      );
      if (!at || at.name === data.value.player)
        throw new Error('Choisis un autre survivant présent.');
      gzRelocated.set(data.value.player, { x: at.x + 2, z: at.z });
      return {
        result: `Démonstration : ${data.value.player} a rejoint ${at.name}.`,
      };
    }
    if (data.action === 'enable') apocalypseEnabled = true;
    if (data.action === 'disable') apocalypseEnabled = false;
    if (data.action === 'set') gzSettings[data.value.key] = data.value.value;
    if (data.action === 'preset')
      Object.assign(gzSettings, gzPresets[data.value].values);
    if (data.action === 'evacuate') evacuated.add(data.value);
    if (data.action === 'give') {
      gzEvents.unshift({
        at: Date.now(),
        kind: 'admin',
        text: `Alex a donné ${data.value.count} × ${gzItems.find((i) => i.id === data.value.item)?.name} à ${data.value.player} (démo)`,
      });
      return { result: 'Démonstration : don simulé, aucun serveur contacté.' };
    }
    if (data.action === 'lives-reset') {
      if (demoApocalypse().survivors.length)
        throw new Error(
          'Fais d’abord sortir tous les survivants de Ground Zero.',
        );
      evacuated.clear();
    }
    if (data.action.startsWith('world-'))
      throw new Error(
        'Le monde de démonstration est déjà associé. Cette opération est simulée uniquement sur un monde absent.',
      );
    apocalypseRules.globalCap = gzSettings['global-cap'];
    if (data.action === 'status' || data.action === 'settings')
      return {
        result:
          'Monde : ground_zero | base : OK\nInventaires classiques gardés : 0\nDémonstration : aucune action sur un serveur réel.',
      };
    if (data.action === 'pvp') apocalypseRules.pvp = !!data.value;
    if (data.action === 'destruction')
      apocalypseRules.destruction = !!data.value;
    if (data.action === 'threat')
      apocalypseThreat = Number(data.value) || apocalypseThreat;
    if (data.action === 'selftest')
      return {
        result: [
          'OK  objets du scénario : 27 objets authentifiés',
          'OK  siège en situation : porte en bois enfoncée par un errant en 14 coups, roche intacte',
          'OK  faune contaminée : vache convertie en bétail infecté',
          'OK  largage : 40 tirages hors refuge, dont 31 en ville',
          'Autotest réussi.',
        ].join('\n'),
      };
    return { result: 'Démonstration : action simulée.' };
  }
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
  if (u.pathname === '/api/jobs') {
    if (data.type === 'restart') {
      demoPlugins.push(
        ...demoPending.map((p) => ({
          name: p.name,
          version: p.version,
          filename: p.name + '.jar',
          core: false,
        })),
      );
      demoPending = [];
    }
    return { id: 'demo-job' };
  }
  if (u.pathname.startsWith('/api/jobs/'))
    return { status: 'done', ok: true, message: 'Opération simulée terminée.' };
  return { ok: true, result: 'Action simulée.' };
}
export { image } from './demo-image';
