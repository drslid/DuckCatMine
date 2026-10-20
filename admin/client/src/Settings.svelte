<script lang="ts">
  import { modal } from './modal';
  import { onMount } from 'svelte';
  import { api, command, bytes, demo } from './api';
  import Icon from './Icon.svelte';
  import { serverHealth } from './health.js';
  let Plugins = $state<any>(null);
  let {
    status = {},
    toast,
    initial = 'game',
  }: { status?: any; toast: any; initial?: string } = $props();
  let mapPolicy = $state('live');
  $effect(() => {
    tab = initial;
  });
  $effect(() => {
    if (tab === 'plugins' && !Plugins)
      import('./Plugins.svelte').then((module) => (Plugins = module.default));
  });
  let tab = $state('game'),
    motd = $state(''),
    maxPlayers = $state(10),
    pvp = $state(false),
    view = $state(8),
    simulation = $state(6),
    busy = $state(false),
    password = $state(''),
    operation = $state(''),
    job = $state(''),
    jobText = $state(''),
    archive = $state(''),
    confirmText = $state(''),
    consoleCommand = $state(''),
    consoleResult = $state(''),
    filter = $state(''),
    current = $state(''),
    next = $state(''),
    stop = false;
  onMount(() => {
    tab = initial;
    mapPolicy = status.mapPolicy || 'live';
    const p = status.properties || {};
    motd = p.motd || 'DuckCatMine';
    maxPlayers = Number(p['max-players'] || 10);
    pvp = p.pvp === 'true';
    view = Number(p['view-distance'] || 8);
    simulation = Number(p['simulation-distance'] || 6);
    return () => {
      stop = true;
    };
  });
  async function save(e) {
    e.preventDefault();
    busy = true;
    try {
      await api('/api/settings', 'POST', {
        motd,
        maxPlayers,
        pvp,
        viewDistance: view,
        simulationDistance: simulation,
      });
      toast('Réglages préparés pour la prochaine relance.');
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function startJob(type) {
    busy = true;
    try {
      const r = await api('/api/jobs', 'POST', {
        type,
        password,
        backup: archive,
        confirm: confirmText,
      });
      operation = '';
      password = '';
      job = r.id;
      jobText =
        type === 'restart'
          ? 'Relance programmée après 5 minutes sans joueur.'
          : 'Opération en attente…';
      toast(jobText);
      watchJob(r.id, type);
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function watchJob(id, type) {
    while (!stop && job === id) {
      try {
        const r = await api(`/api/jobs/${id}`);
        if (r.status === 'done') {
          jobText = r.message || r.error;
          toast(jobText, !r.ok);
          job = '';
          return;
        }
        jobText =
          r.status === 'running'
            ? 'Opération en cours…'
            : type === 'restart'
              ? 'Attend 5 minutes sans joueur, puis sauvegarde et relance.'
              : 'Opération en attente…';
      } catch (e) {
        jobText = e.message;
      }
      await new Promise((r) =>
        setTimeout(r, type === 'restart' ? 10000 : 2000),
      );
    }
  }
  async function applyMapPolicy() {
    busy = true;
    try {
      const r = await api('/api/map/policy', 'POST', { policy: mapPolicy });
      job = r.id;
      jobText = 'Changement de la carte en cours...';
      toast(jobText);
      watchJob(r.id, 'map-policy');
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function sendCommand(e) {
    e.preventDefault();
    try {
      const r = await command(consoleCommand);
      consoleResult = `> ${consoleCommand}\n${r.result || 'Commande appliquée.'}`;
      consoleCommand = '';
      toast('Commande exécutée.');
    } catch (e) {
      toast(e.message, true);
    }
  }
  async function changePassword(e) {
    e.preventDefault();
    try {
      await api('/api/password', 'POST', {
        currentPassword: current,
        newPassword: next,
      });
      location.reload();
    } catch (e) {
      toast(e.message, true);
    }
  }
</script>

<div class="section-heading">
  <div>
    <p class="eyebrow">À TON RYTHME</p>
    <h1>Réglages du serveur</h1>
  </div>
</div>
<div class="segments wrap settings-tabs">
  {#each [['game', 'Partie'], ['plugins', 'Extensions'], ['backups', 'Sauvegardes'], ['performance', 'Performances'], ['console', 'Console'], ['account', 'Mon compte']] as [id, label]}<button
      class:active={tab === id}
      onclick={() => (tab = id)}>{label}</button
    >{/each}
</div>
{#if tab === 'game'}<div class="settings-layout">
    <form class="card settings-form" onsubmit={save}>
      <h2>La partie</h2>
      <label
        >Nom du serveur<input
          bind:value={motd}
          maxlength="80"
          required
        /></label
      >
      <div class="form-grid">
        <label
          >Places maximum<input
            type="number"
            min="2"
            max="50"
            bind:value={maxPlayers}
            required
          /></label
        ><label
          >Combat entre joueurs<select bind:value={pvp}
            ><option value={false}>Désactivé</option><option value={true}
              >Activé</option
            ></select
          ></label
        >
      </div>
      <details class="disclosure">
        <summary>Distances et consommation</summary>
        <div class="form-grid">
          <label
            >Distance de vue<input
              type="number"
              min="4"
              max="16"
              bind:value={view}
            /></label
          ><label
            >Distance de simulation<input
              type="number"
              min="4"
              max="12"
              bind:value={simulation}
            /></label
          >
        </div>
        <p class="muted">
          Une simulation plus courte réduit le travail du serveur et la portée
          d’activité des fermes.
        </p>
        <button
          class="quiet"
          type="button"
          onclick={() => {
            view = 8;
            simulation = 6;
          }}>Profil équilibré · 8 / 6</button
        >
      </details>
      <button class="primary" disabled={busy}>Préparer les changements</button
      ><small class="muted"
        >Ces réglages s’appliquent à la prochaine relance.</small
      >
    </form>
    <div>
      <article class="card">
        <h2>Sauvegarder et relancer</h2>
        <p class="muted">
          Charge les plugins et les réglages préparés. Attend cinq minutes sans
          joueur, puis sauvegarde le monde avant la relance.
        </p>
        {#if status.scheduledRestart}<p class="pill">
            Relance déjà programmée
          </p>{:else}<button
            class="secondary"
            onclick={() => (operation = 'restart')}
            ><Icon name="refresh" />Programmer la relance</button
          >{/if}
      </article>
      {#if Object.keys(status.pending || {}).length}<article class="card">
          <h2>Changements en attente</h2>
          <dl class="key-values">
            {#each Object.entries(status.pending) as [key, value]}<dt>
                {{
                  motd: 'Nom',
                  maxPlayers: 'Places',
                  pvp: 'PvP',
                  viewDistance: 'Vue',
                  simulationDistance: 'Simulation',
                }[key] || key}
              </dt>
              <dd>{String(value)}</dd>{/each}
          </dl>
          <button class="secondary" onclick={() => (operation = 'apply')}
            >Sauvegarder et appliquer maintenant</button
          ><small class="muted"
            >Disponible lorsque tous les joueurs sont déconnectés.</small
          >
        </article>{/if}
      <details class="disclosure card">
        <summary>Règles de la partie à chaud</summary
        >{#each Object.entries(status.gamerules || {}) as [rule, value]}<div
            class="setting-row"
          >
            <span
              >{{
                keep_inventory: 'Garder l’inventaire après la mort',
                advance_time: 'Cycle jour / nuit',
                spawn_mobs: 'Apparition des créatures',
                mob_griefing: 'Dégâts des créatures',
                players_sleeping_percentage:
                  'Joueurs nécessaires pour dormir (%)',
              }[rule] || rule}</span
            >{#if ['true', 'false'].includes(String(value))}<button
                class="secondary"
                onclick={async () => {
                  try {
                    await command(
                      `gamerule ${rule} ${value === 'true' ? 'false' : 'true'}`,
                    );
                    toast('Règle appliquée.');
                  } catch (e) {
                    toast(e.message, true);
                  }
                }}>{value === 'true' ? 'Activé' : 'Désactivé'}</button
              >{:else}<strong>{String(value)}</strong>{/if}
          </div>{/each}
      </details>
    </div>
  </div>
{:else if tab === 'backups'}<article class="card">
    <div class="card-heading">
      <div>
        <h2>Protéger le monde</h2>
        <p class="muted">Crée une sauvegarde sans déconnecter les joueurs.</p>
      </div>
      <button class="primary" disabled={busy} onclick={() => startJob('backup')}
        ><Icon name="save" />Sauvegarder</button
      >
    </div>
    {#if jobText}<p role="status">{jobText}</p>{/if}
    <div class="backup-list">
      {#each status.backups || [] as b}<div class="backup-entry">
          <div>
            <strong>{new Date(b.at).toLocaleString('fr-FR')}</strong><small
              >{bytes(b.size)}</small
            >
          </div>
          <a class="quiet" href={`/api/backups/${encodeURIComponent(b.name)}`}
            >Télécharger</a
          ><button
            class="quiet"
            onclick={() => {
              archive = b.name;
              operation = 'restore';
            }}>Restaurer</button
          >
        </div>{/each}{#if !status.backups?.length}<p class="empty">
          Les sauvegardes apparaîtront ici.
        </p>{/if}
    </div>
  </article>
{:else if tab === 'plugins' && Plugins}<Plugins
    {toast}
    scheduled={!!status.scheduledRestart || !!job}
    restart={() => (operation = 'restart')}
  />
  {#if jobText}<p role="status">{jobText}</p>{/if}
{:else if tab === 'performance'}<article
    class="card health-diagnostic"
    data-health={serverHealth(status).level}
  >
    <Icon name="heart" size={26} />
    <div>
      <h2>{serverHealth(status).label}</h2>
      <p class="muted">{serverHealth(status).detail}</p>
      <small class="muted"
        >Le CPU du bandeau représente la part du VPS utilisée par Minecraft. La
        RAM est celle du conteneur du jeu, limite comprise.</small
      >
    </div>
  </article>
  <article class="card map-policy-card">
    <h2>Priorité de la carte</h2>
    <p class="muted">
      Les positions des joueurs restent en direct. Pour réduire la charge, le
      dessin du terrain peut attendre la fin de la partie.
    </p>
    <label
      >Actualisation du terrain<select bind:value={mapPolicy}
        ><option value="idle">Priorité au jeu : sans joueur connecté</option
        ><option value="live">Pendant les parties aussi</option></select
      ></label
    ><button class="secondary" disabled={busy} onclick={applyMapPolicy}
      >Appliquer sans relancer Minecraft</button
    >{#if jobText}<p role="status">{jobText}</p>{/if}
  </article>
  <article class="card">
    <h2>Comprendre la consommation</h2>
    <p class="muted">
      Les valeurs CPU des conteneurs sont exprimées par cœur : 100 % correspond
      à un vCPU utilisé.
    </p>
    <div class="metrics-grid">
      <div>
        <small>MINECRAFT</small><strong>{status.metrics?.cpu || '—'}</strong
        ><span>CPU, par cœur</span>
      </div>
      <div>
        <small>MÉMOIRE DU JEU</small><strong
          >{status.metrics?.memory?.split('/')[0] || '—'}</strong
        ><span>Consommation du conteneur</span>
      </div>
      <div>
        <small>MÉMOIRE DISPONIBLE</small><strong
          >{bytes(status.metrics?.host?.MemAvailable)}</strong
        ><span>Sur la machine</span>
      </div>
    </div>
    <div class="service-list">
      {#each status.metrics?.services || [] as s}<div class="setting-row">
          <span
            >{s.name
              .replace('duckcatmine-admin-', '')
              .replace('duckcatmine-', '')
              .replace('-1', '')}</span
          ><strong>{bytes(s.memoryBytes)}</strong>
        </div>{/each}
    </div>
    <p class="muted">
      Pour identifier un pic, enregistre un profil spark pendant qu’il se
      produit.
    </p>
    <button
      class="secondary"
      onclick={async () => {
        try {
          const r = await command('spark profiler start --timeout 120');
          toast(r.result || 'Profil lancé pour deux minutes.');
        } catch (e) {
          toast(e.message, true);
        }
      }}>Analyser pendant 2 minutes</button
    >
  </article>
{:else if tab === 'console'}<article class="card">
    <h2>Console Minecraft</h2>
    <form class="inline" onsubmit={sendCommand}>
      <input
        bind:value={consoleCommand}
        required
        maxlength="240"
        aria-label="Commande Minecraft"
        placeholder="Commande sans /"
      /><button class="primary">Exécuter</button>
    </form>
    {#if consoleResult}<pre>{consoleResult}</pre>{/if}
    <details class="disclosure">
      <summary>Journal du serveur</summary><input
        type="search"
        aria-label="Filtrer le journal"
        bind:value={filter}
        placeholder="Filtrer…"
      />
      <pre class="log-output">{(status.logs || '')
          .split('\n')
          .filter((l) => l.toLowerCase().includes(filter.toLowerCase()))
          .join('\n')}</pre>
    </details>
  </article>
{:else if tab === 'account'}<article class="card account-form">
    <h2>Changer mon mot de passe</h2>
    <form onsubmit={changePassword}>
      <label
        >Mot de passe actuel<input
          type="password"
          autocomplete="current-password"
          bind:value={current}
          required
        /></label
      ><label
        >Nouveau mot de passe<input
          type="password"
          autocomplete="new-password"
          bind:value={next}
          minlength="12"
          maxlength="128"
          required
        /></label
      ><button class="primary">Enregistrer</button>
    </form>
  </article>{/if}
{#if operation}<div class="modal-backdrop" role="presentation">
    <div
      class="modal"
      use:modal={{ close: () => (operation = '') }}
      role="dialog"
      aria-modal="true"
      aria-label="Confirmer l’opération"
      tabindex="-1"
    >
      <div class="modal-heading">
        <h2>
          {operation === 'restore'
            ? 'Restaurer le monde'
            : operation === 'apply'
              ? 'Appliquer les réglages'
              : 'Programmer une relance'}
        </h2>
        <button
          class="icon-button"
          aria-label="Fermer"
          onclick={() => {
            operation = '';
            password = '';
          }}><Icon name="close" /></button
        >
      </div>
      <p>
        {operation === 'restore'
          ? 'Le monde actuel sera remplacé par l’archive choisie. Une copie de sécurité sera conservée.'
          : 'Une sauvegarde du monde sera créée avant le redémarrage.'}
      </p>
      <form
        onsubmit={(e) => {
          e.preventDefault();
          startJob(operation);
        }}
      >
        {#if operation === 'restore'}<label
            >Tape RESTAURER<input
              bind:value={confirmText}
              pattern="RESTAURER"
              required
            /></label
          >{/if}<label
          >Confirme avec ton mot de passe<input
            type="password"
            autocomplete="current-password"
            bind:value={password}
            required
          /></label
        ><button class="primary full" disabled={busy}>Confirmer</button>
      </form>
    </div>
  </div>{/if}
