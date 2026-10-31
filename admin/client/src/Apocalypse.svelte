<script lang="ts">
  import { onMount } from 'svelte';
  import { api, demo, fold, iconUrl } from './api';
  import { modal } from './modal';
  import Icon from './Icon.svelte';
  import GroundZeroRules from './GroundZeroRules.svelte';
  import GroundZeroMaintenance from './GroundZeroMaintenance.svelte';
  let {
    toast,
    openPlayer,
    openMaintenance = () => {},
  }: {
    toast: (message: string, error?: boolean) => void;
    openPlayer: (name: string) => void;
    openMaintenance?: () => void;
  } = $props();
  let status = $state<any>(null),
    tab = $state('map'),
    busy = $state(''),
    loadError = $state('');
  let selected = $state(''),
    search = $state(''),
    itemId = $state('antidote'),
    count = $state(1);
  let intent = $state<any>(null),
    confirmText = $state(''),
    report = $state<string[]>([]);
  let context = $state<HTMLElement>();
  let loading = false,
    disposed = false,
    refreshAfter = 0;
  const phases: Record<string, string> = {
    calm: 'Calme',
    signs: 'Indices',
    pressure: 'Pression',
    peak: 'Horde',
    relief: 'Répit',
  };
  const oldIcons: Record<string, string> = {
    antidote: 'experience_bottle',
    scrap: 'iron_nugget',
    cloth: 'white_carpet',
    tape: 'gray_dye',
    spring: 'tripwire_hook',
    gun_parts: 'netherite_scrap',
    casings: 'gold_nugget',
    propellant: 'gunpowder',
    alcohol: 'honey_bottle',
    syringe: 'end_rod',
    serum: 'dragon_breath',
    inhibitor: 'prismarine_crystals',
    bandage: 'paper',
    club: 'wooden_shovel',
    blade: 'stone_sword',
    spear: 'stone_spear',
    heavy_club: 'mace',
    pistol: 'crossbow',
    ammo: 'copper_nugget',
    decoy: 'note_block',
    workshop: 'smithing_table',
    medical_station: 'brewing_stand',
    barricade: 'dark_oak_fence',
    barricade_reinforced: 'waxed_weathered_copper_grate',
    spike_trap: 'pointed_dripstone',
    beacon: 'lodestone',
    pipe_bomb: 'tnt',
    molotov: 'splash_potion',
    fire_axe: 'iron_axe',
    bow: 'bow',
    arrow: 'arrow',
    jerrycan: 'bucket',
  };
  let survivors = $derived<any[]>(status?.survivors || []);
  let survivor = $derived(survivors.find((s) => s.name === selected));
  let map = $derived(status?.map);
  let items = $derived<any[]>(
    (status?.items || []).filter((item: any) =>
      fold(item.name + ' ' + item.id).includes(fold(search)),
    ),
  );
  let chosenItem = $derived(
    (status?.items || []).find((item: any) => item.id === itemId),
  );
  let open = $derived(!!status?.enabled && !status?.stale);

  async function load() {
    if (loading || disposed) return;
    loading = true;
    try {
      const next = await api('/api/apocalypse');
      if (disposed) return;
      if (!next.stale && next.generatedAt && next.generatedAt <= refreshAfter)
        return;
      status = next;
      loadError = '';
    } catch (e) {
      loadError = e.message;
    } finally {
      loading = false;
    }
  }
  onMount(() => {
    disposed = false;
    load();
    const timer = setInterval(() => {
      if (!document.hidden) load();
    }, 5000);
    const visible = () => {
      if (!document.hidden) load();
    };
    document.addEventListener('visibilitychange', visible);
    return () => {
      disposed = true;
      clearInterval(timer);
      document.removeEventListener('visibilitychange', visible);
    };
  });
  function ask(value: any) {
    intent = value;
    confirmText = '';
  }
  async function act(action: string, value?: any, confirmation?: string) {
    if (busy) return false;
    busy = action;
    try {
      const result = await api('/api/apocalypse/action', 'POST', {
        action,
        value,
        confirmation,
      });
      const text = String(result.result || '').trim();
      if (['status', 'settings', 'selftest'].includes(action))
        report = text.split('\n').filter(Boolean);
      else
        toast(
          text.split('\n').filter(Boolean).at(-1) ||
            'Commande transmise. Le panneau va s’actualiser.',
        );
      refreshAfter = Number(status?.generatedAt || 0);
      if (action === 'set')
        status = {
          ...status,
          settings: { ...status.settings, [value.key]: value.value },
        };
      if (action === 'preset' && status.presets?.[value])
        status = {
          ...status,
          settings: { ...status.settings, ...status.presets[value].values },
        };
      if (action === 'enable' || action === 'disable')
        status = { ...status, enabled: action === 'enable' };
      if (action === 'pvp' || action === 'destruction')
        status = { ...status, rules: { ...status.rules, [action]: value } };
      if (action === 'threat')
        status = { ...status, threat: { ...status.threat, level: value } };
      intent = null;
      load();
      return true;
    } catch (e) {
      toast(e.message, true);
      if (['status', 'settings', 'selftest'].includes(action))
        report = [e.message];
      return false;
    } finally {
      busy = '';
    }
  }
  function duration(seconds: number) {
    const s = Math.max(0, Math.floor(seconds || 0));
    return s >= 3600
      ? `${Math.floor(s / 3600)} h ${Math.floor((s % 3600) / 60)} min`
      : `${Math.floor(s / 60)} min ${String(s % 60).padStart(2, '0')}`;
  }
  function onMap(x: number, z: number) {
    return (
      map &&
      x >= map.minX &&
      z >= map.minZ &&
      x < map.minX + map.size &&
      z < map.minZ + map.size
    );
  }
  function choose(name: string) {
    selected = name;
    requestAnimationFrame(() => {
      if (window.innerWidth < 900)
        context?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }
  function itemIcon(item: any) {
    return iconUrl(item.icon || `minecraft:${oldIcons[item.id] || 'paper'}`);
  }
</script>

<div class="section-heading gz-heading">
  <div>
    <p class="eyebrow">SURVIVRE ENSEMBLE</p>
    <h1>Ground Zero</h1>
  </div>
  {#if status?.installed}<div class="inline header-actions">
      <span class="pill" class:alive={open}
        >{status.stale
          ? 'Hors ligne'
          : status.enabled
            ? 'Ouvert'
            : 'En pause'}</span
      >
      <button
        class={status.enabled ? 'secondary' : 'primary'}
        disabled={!!busy || status.stale || (!status.enabled && !status.world)}
        onclick={() =>
          status.enabled
            ? ask({
                action: 'disable',
                title: 'Mettre Ground Zero en pause',
                description:
                  'Les survivants présents retrouvent leur survie habituelle. Leurs vies sont sauvegardées et les horloges sont figées.',
              })
            : act('enable')}
        ><Icon
          name={status.enabled ? 'moon' : 'sun'}
          size={16}
        />{status.enabled ? 'Mettre en pause' : 'Ouvrir la partie'}</button
      >
    </div>{/if}
</div>

{#if loadError}<p class="notice error" role="alert">
    {loadError} <button class="quiet" onclick={load}>Réessayer</button>
  </p>{/if}
{#if !status}<p class="muted">Lecture de la partie…</p>
{:else if !status.installed}<article class="card empty large">
    <Icon name="skull" size={34} />
    <h2>Ground Zero n’est pas installé</h2>
    <p>
      Ajoute l’extension DuckCatApocalypse, puis crée son monde dédié depuis
      cette page.
    </p>
  </article>
{:else}
  {#if status.stale}<p class="notice" role="status">
      Le scénario ne répond plus depuis {duration(status.ageSeconds)}. Les
      actions seront disponibles à son retour.
    </p>{/if}
  {#if status.problem || status.configProblem}<p
      class="notice error"
      role="alert"
    >
      {status.problem || status.configProblem}
    </p>{/if}
  <div class="segments gz-tabs" aria-label="Administration Ground Zero">
    {#each [{ id: 'map', label: 'Carte', icon: 'map' }, { id: 'rules', label: 'Règles', icon: 'settings' }, { id: 'maintenance', label: 'Maintenance', icon: 'shield' }] as section}
      <button
        class:active={tab === section.id}
        aria-pressed={tab === section.id}
        onclick={() => (tab = section.id)}
        ><Icon name={section.icon} size={17} />{section.label}</button
      >
    {/each}
  </div>

  {#if tab === 'rules'}<GroundZeroRules {status} {busy} {act} {ask} />
  {:else if tab === 'maintenance'}<GroundZeroMaintenance
      {status}
      {busy}
      {report}
      {act}
      {ask}
      {openMaintenance}
    />
  {:else}
    <div class="gz-summary" aria-label="État de la partie">
      <span
        ><Icon name="users" size={16} /><strong>{survivors.length}</strong>
        survivant{survivors.length > 1 ? 's' : ''}</span
      >
      <span
        ><Icon name="skull" size={16} /><strong
          >{status.infectedTotal ?? 0}</strong
        > infectés</span
      >
      <span
        ><Icon name={status.time?.night ? 'moon' : 'sun'} size={16} />Jour {(status
          .time?.day ?? 0) + 1}</span
      >
      <span class:pressure={['pressure', 'peak'].includes(status.threat?.phase)}
        ><strong>{Number(status.threat?.level || 1).toFixed(1)}</strong> menace
        · {phases[status.threat?.phase] || '—'}</span
      >
    </div>
    <div class="gz-workspace">
      <article class="card map-card">
        <div class="card-heading">
          <div>
            <h2>Votre terrain de survie</h2>
            <p class="muted small">
              Sélectionne un survivant sur la carte ou dans la liste.
            </p>
          </div>
          <button
            class="icon-button"
            aria-label="Options de rendu de la carte"
            onclick={() => (tab = 'maintenance')}
            ><Icon name="settings" size={18} /></button
          >
        </div>
        {#if map && status.mapRenderedAt}
          <svg
            class="gz-map"
            viewBox={`0 0 ${map.size} ${map.size}`}
            role="group"
            aria-label="Carte de Ground Zero avec les survivants"
          >
            {#if demo}
              <defs
                ><pattern
                  id="gz-city-blocks"
                  width="80"
                  height="80"
                  patternUnits="userSpaceOnUse"
                  ><rect width="80" height="80" fill="#394b3c" /><path
                    d="M0 0H80V15H15V80H0Z"
                    fill="#6e7064"
                  /><rect
                    x="23"
                    y="23"
                    width="44"
                    height="44"
                    rx="2"
                    fill="#87927b"
                  /><rect
                    x="29"
                    y="29"
                    width="32"
                    height="32"
                    fill="#596c5e"
                  /><path
                    d="M30 39h12m7 9h9"
                    stroke="#c4b694"
                    stroke-width="3"
                  /></pattern
                ></defs
              >
              <rect
                width={map.size}
                height={map.size}
                fill="url(#gz-city-blocks)"
              /><path
                d={`M0 ${map.size * 0.7}Q${map.size * 0.5} ${map.size * 0.55} ${map.size} ${map.size * 0.85}`}
                stroke="#4c7776"
                stroke-width="34"
                fill="none"
              />
              <rect
                x={map.size / 2 - 22}
                y={map.size / 2 - 22}
                width="44"
                height="44"
                fill="#a4ab81"
                stroke="#d9c99c"
                stroke-width="5"
              />
            {:else}<image
                href={`/api/apocalypse/map.png?v=${Math.round(status.mapRenderedAt)}`}
                width={map.size}
                height={map.size}
              />{/if}
            {#if status.refuge}<circle
                class="refuge"
                cx={status.refuge.x - map.minX}
                cy={status.refuge.z - map.minZ}
                r={Math.max(status.refuge.radius, map.size / 100)}
              />{/if}
            {#each survivors.filter((s) => onMap(s.x, s.z)) as s (s.name)}
              <g
                class="dot"
                class:bitten={s.infectionSeconds != null}
                class:selected={selected === s.name}
                transform={`translate(${s.x - map.minX} ${s.z - map.minZ})`}
                role="button"
                tabindex="0"
                aria-label={`Sélectionner ${s.name}`}
                onclick={() => choose(s.name)}
                onkeydown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    choose(s.name);
                  }
                }}
              >
                <circle class="hit-area" r={map.size / 32} /><circle
                  r={map.size / 65}
                /><text y={-map.size / 38} font-size={map.size / 34}
                  >{s.name}</text
                >
              </g>
            {/each}
          </svg>
          <div class="map-caption">
            <span>Nord ↑ · {map.size} × {map.size} blocs</span><span
              >Positions · 5 s</span
            >
          </div>
          <p class="muted small">
            Terrain du {new Date(status.mapRenderedAt).toLocaleDateString(
              'fr-FR',
            )}. Actualise son rendu dans Maintenance après de grandes
            constructions.
          </p>
        {:else}<div class="map-empty">
            <Icon name="map" size={42} />
            <h3>Préparons votre carte</h3>
            <p>
              Un rendu du terrain permettra de repérer le refuge et vos
              survivants.
            </p>
            <button
              class="secondary"
              disabled={status.stale || !status.world}
              onclick={() => (tab = 'maintenance')}>Préparer le rendu</button
            >
          </div>{/if}
        <div class="survivor-list" aria-label="Survivants présents">
          {#each survivors as s (s.name)}<button
              class="survivor-chip"
              class:selected={selected === s.name}
              onclick={() => choose(s.name)}
            >
              <span class="account-avatar">{s.name.slice(0, 2)}</span><span
                ><strong>{s.name}</strong><small
                  >{s.inRefuge
                    ? 'Au refuge'
                    : s.sector || 'En exploration'}</small
                ></span
              >
              {#if s.infectionSeconds != null}<span
                  class="bite-dot"
                  aria-label="Mordu"
                ></span>{/if}</button
            >
          {:else}<p class="muted small">
              Personne dans Ground Zero pour l’instant.
            </p>{/each}
        </div>
      </article>

      <aside
        class="card gz-context"
        bind:this={context}
        aria-label="Actions sur le survivant"
      >
        {#if survivor}
          <div class="card-heading">
            <div class="person-heading">
              <span class="account-avatar">{survivor.name.slice(0, 2)}</span>
              <div>
                <h2>{survivor.name}</h2>
                <small class="muted"
                  >{survivor.inRefuge
                    ? 'Au refuge'
                    : survivor.sector || 'En exploration'}</small
                >
              </div>
            </div>
            <button
              class="icon-button"
              aria-label="Fermer la sélection"
              onclick={() => (selected = '')}
              ><Icon name="close" size={16} /></button
            >
          </div>
          <div class="survivor-facts">
            <span
              ><Icon name="heart" size={15} />{survivor.health ?? '—'} / 20</span
            ><span>{duration(survivor.lifeSeconds)}</span><span
              >{survivor.points ?? 0} points</span
            >
          </div>
          {#if survivor.infectionSeconds != null}<p class="notice error">
              <Icon name="heart" size={16} />Morsure · {survivor.infectionSeconds}
              s pour finir l’antidote.
            </p>{/if}
          {#if survivor.team}<p class="muted small">
              Équipe · {survivor.team}
            </p>{/if}
          <h3>Donner un objet Ground Zero</h3>
          <p class="muted small">
            Don administrateur identifié dans le journal.
          </p>
          <label class="item-search"
            ><Icon name="search" size={16} /><input
              aria-label="Chercher un objet Ground Zero"
              placeholder="Antidote, arme, matériel…"
              bind:value={search}
            /></label
          >
          <div class="gz-items" aria-label="Catalogue Ground Zero">
            {#each items as item (item.id)}<button
                class:chosen={itemId === item.id}
                aria-pressed={itemId === item.id}
                title={item.name}
                onclick={() => (itemId = item.id)}
              >
                <img
                  src={itemIcon(item)}
                  alt=""
                  loading="lazy"
                  width="32"
                  height="32"
                /><span>{item.name}</span></button
              >{/each}
          </div>
          {#if !items.length}<p class="muted small">
              Aucun objet correspondant.
            </p>{/if}
          <form
            class="give-form"
            onsubmit={(e) => {
              e.preventDefault();
              ask({
                action: 'give',
                value: { player: survivor.name, item: itemId, count },
                title: 'Confirmer le don',
                description: `Donner ${count} × ${chosenItem?.name || itemId} à ${survivor.name}. Le don sera tracé.`,
              });
            }}
          >
            <label
              >Quantité<input
                type="number"
                min="1"
                max="64"
                bind:value={count}
                required
              /></label
            ><button
              class="primary"
              disabled={!!busy || status.stale || !chosenItem}
              ><Icon name="box" size={16} />Donner</button
            >
          </form>
          <div class="survivor-actions">
            <button
              class="secondary"
              disabled={!!busy || status.stale || survivor.inRefuge}
              onclick={() =>
                ask({
                  action: 'horde',
                  value: survivor.name,
                  title: 'Envoyer une horde',
                  description: `Envoyer les infectés vers ${survivor.name}. Cette action rend sa situation plus dangereuse.`,
                  danger: true,
                })}><Icon name="skull" size={16} />Envoyer une horde</button
            >
            <button
              class="secondary"
              disabled={!!busy || status.stale}
              onclick={() =>
                ask({
                  action: 'evacuate',
                  value: survivor.name,
                  title: 'Évacuer le survivant',
                  description: `Ramener ${survivor.name} dans sa survie habituelle. Sa vie Ground Zero est sauvegardée pour son retour.`,
                })}
              ><Icon name="logout" size={16} />Évacuer vers la survie</button
            >
            <button class="quiet" onclick={() => openPlayer(survivor.name)}
              ><Icon name="users" size={16} />Fiche du joueur</button
            >
          </div>
        {:else}<div class="context-empty">
            <Icon name="users" size={30} />
            <h2>
              {selected
                ? 'Ce survivant a quitté la partie'
                : 'Un joueur, les bonnes actions'}
            </h2>
            <p class="muted">
              Sélectionne un survivant pour voir son état, choisir un objet,
              envoyer une horde ou le ramener dans sa survie habituelle.
            </p>
          </div>{/if}
      </aside>
    </div>

    <details class="card session-details">
      <summary
        ><Icon name="chat" size={17} />Journal, équipes et classement</summary
      >
      <div class="session-columns">
        <section>
          <h3>Derniers événements</h3>
          <ul class="journal">
            {#each (status.events || []).slice(0, 8) as event}<li>
                <time
                  >{new Date(event.at).toLocaleTimeString('fr-FR', {
                    hour: '2-digit',
                    minute: '2-digit',
                  })}</time
                >{event.text}
              </li>{:else}<li class="muted">
                Le journal se remplira pendant la partie.
              </li>{/each}
          </ul>
        </section>
        <section>
          <h3>Équipes</h3>
          {#each status.teams || [] as team}<p>
              <strong>{team.name}</strong><br /><span class="muted small"
                >{team.members.join(', ')}</span
              >
            </p>{:else}<p class="muted small">Aucune équipe.</p>{/each}
          <h3>Survie et points</h3>
          <ul class="ranking">
            {#each (status.leaderboard || []).slice(0, 5) as player}<li>
                <strong>{player.name}</strong><span
                  >{player.points ?? 0} pts</span
                >
              </li>{:else}<li class="muted small">
                Le classement apparaîtra après vos premières vies.
              </li>{/each}
          </ul>
        </section>
      </div>
    </details>
  {/if}
{/if}

{#if intent}<div class="modal-backdrop" role="presentation">
    <div
      class="modal"
      use:modal={{
        close: () => {
          if (!busy) intent = null;
        },
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="gz-confirm-title"
      tabindex="-1"
    >
      <div class="modal-heading">
        <h2 id="gz-confirm-title">{intent.title}</h2>
        <button
          class="icon-button"
          aria-label="Fermer la confirmation"
          disabled={!!busy}
          onclick={() => (intent = null)}><Icon name="close" /></button
        >
      </div>
      <p>{intent.description}</p>
      <form
        onsubmit={(e) => {
          e.preventDefault();
          if (!intent.required || confirmText === intent.required)
            act(intent.action, intent.value, intent.required);
        }}
      >
        {#if intent.required}<label
            >Tape {intent.required} pour confirmer<input
              bind:value={confirmText}
              autocomplete="off"
              autocapitalize="off"
              spellcheck="false"
              required
            /></label
          >{/if}
        <div class="confirmation-actions">
          <button
            type="button"
            class="secondary"
            disabled={!!busy}
            onclick={() => (intent = null)}>Annuler</button
          ><button
            class={intent.danger ? 'danger' : 'primary'}
            disabled={!!busy ||
              (!!intent.required && confirmText !== intent.required)}
            >{busy ? 'Application…' : 'Confirmer'}</button
          >
        </div>
      </form>
    </div>
  </div>{/if}

<style>
  .gz-heading {
    gap: 12px;
    flex-wrap: wrap;
  }
  .header-actions {
    gap: 9px;
    flex-wrap: wrap;
  }
  .pill.alive {
    color: var(--accent);
    border-color: var(--accent);
  }
  .notice {
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 11px 13px;
    margin: 0 0 14px;
    font-size: 12px;
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--surface2);
    color: var(--soft);
  }
  .notice.error {
    color: #f3ab99;
    border-color: #805446;
  }
  .notice :global(svg) {
    flex-shrink: 0;
  }
  .small {
    font-size: 11px;
    line-height: 1.6;
  }
  .gz-tabs {
    margin: 0 0 18px;
    width: fit-content;
    max-width: 100%;
  }
  .gz-tabs button {
    gap: 7px;
  }
  .gz-summary {
    display: flex;
    flex-wrap: wrap;
    gap: 12px 22px;
    margin: 0 0 15px;
    font-size: 12px;
    color: var(--muted);
  }
  .gz-summary span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .gz-summary strong {
    color: var(--text);
  }
  .gz-summary .pressure {
    color: #d8af86;
  }
  .gz-workspace {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(270px, 320px);
    gap: 14px;
    align-items: start;
  }
  .map-card,
  .gz-context {
    min-width: 0;
  }
  .map-card .card-heading p {
    margin: 5px 0 0;
  }
  .gz-map {
    width: 100%;
    max-height: 560px;
    display: block;
    background: #17221b;
    border: 1px solid var(--border);
    border-radius: 12px;
  }
  .gz-map .refuge {
    fill: #c0dd9633;
    stroke: #c0dd96;
    stroke-width: 3;
  }
  .dot {
    cursor: pointer;
  }
  .dot .hit-area {
    fill: transparent;
    stroke: none;
  }
  .dot circle {
    fill: #d8cc92;
    stroke: #223321;
    stroke-width: 2;
  }
  .dot.bitten circle {
    fill: #ee8c73;
  }
  .dot.selected circle,
  .dot:focus circle {
    stroke: #fff;
    stroke-width: 5;
  }
  .dot text {
    fill: white;
    text-anchor: middle;
    paint-order: stroke;
    stroke: #17241c;
    stroke-width: 4px;
    font-weight: 650;
    pointer-events: none;
  }
  .map-caption {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
    margin: 10px 0 0;
    font-size: 10px;
    color: var(--muted);
  }
  .map-empty {
    display: grid;
    justify-items: center;
    align-content: center;
    min-height: 300px;
    text-align: center;
    color: var(--muted);
    gap: 9px;
    background: var(--surface2);
    border-radius: 12px;
    padding: 24px;
  }
  .map-empty h3 {
    color: var(--text);
  }
  .map-empty p {
    max-width: 330px;
    font-size: 12px;
  }
  .survivor-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 14px;
  }
  .survivor-chip {
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 8px 10px;
    min-width: 0;
    background: var(--surface2);
    text-align: left;
  }
  .survivor-chip.selected {
    border-color: var(--accent);
  }
  .survivor-chip strong {
    font-size: 11px;
    overflow-wrap: anywhere;
  }
  .survivor-chip small {
    display: block;
    color: var(--muted);
    font-size: 10px;
    margin-top: 2px;
  }
  .bite-dot {
    width: 7px;
    height: 7px;
    background: #ee8c73;
    border-radius: 50%;
  }
  .gz-context {
    scroll-margin: 90px 0;
  }
  .person-heading {
    display: flex;
    gap: 8px;
    align-items: center;
    min-width: 0;
  }
  .person-heading h2 {
    overflow-wrap: anywhere;
    font-size: 14px;
  }
  .person-heading small {
    font-size: 10px;
  }
  .survivor-facts {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    font-size: 11px;
    color: var(--soft);
    margin: 14px 0;
  }
  .survivor-facts span {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  h3 {
    font-size: 12px;
    margin: 17px 0 6px;
  }
  .item-search {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 5px;
    background: var(--surface2);
    border: 1px solid var(--border);
    border-radius: 9px;
    padding: 0 9px;
  }
  .item-search input {
    border: 0;
    padding-left: 3px;
    width: 100%;
    min-width: 0;
    background: transparent;
  }
  .gz-items {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
    max-height: 250px;
    overflow: auto;
    margin: 10px 0;
    padding: 2px;
  }
  .gz-items button {
    display: grid;
    justify-items: center;
    gap: 6px;
    padding: 9px 4px;
    background: var(--surface2);
    border-radius: 9px;
  }
  .gz-items button.chosen {
    border-color: var(--accent);
    background: #8ea87022;
  }
  .gz-items img {
    image-rendering: pixelated;
    object-fit: contain;
  }
  .gz-items span {
    font-size: 10px;
    line-height: 1.3;
    text-align: center;
    overflow-wrap: anywhere;
  }
  .give-form {
    display: grid;
    grid-template-columns: 92px minmax(0, 1fr);
    gap: 8px;
    align-items: end;
  }
  .give-form input {
    width: 100%;
    min-width: 0;
  }
  .give-form button {
    justify-content: center;
  }
  .survivor-actions {
    display: grid;
    gap: 8px;
    margin-top: 17px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }
  .survivor-actions button {
    justify-content: flex-start;
    font-size: 11px;
  }
  .context-empty {
    display: grid;
    justify-items: start;
    gap: 10px;
    padding: 10px 0;
  }
  .context-empty h2 {
    font-size: 14px;
    line-height: 1.5;
  }
  .context-empty p {
    font-size: 12px;
    line-height: 1.7;
  }
  .session-details {
    margin-top: 14px;
  }
  .session-details summary {
    display: flex;
    align-items: center;
    gap: 9px;
    cursor: pointer;
    font-size: 12px;
    font-weight: 600;
  }
  .session-columns {
    display: grid;
    grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
    gap: 28px;
  }
  .journal,
  .ranking {
    list-style: none;
    padding: 0;
    display: grid;
    gap: 9px;
    font-size: 11px;
  }
  .journal li {
    display: flex;
    align-items: baseline;
    gap: 9px;
    overflow-wrap: anywhere;
  }
  .journal time {
    color: var(--muted);
    flex-shrink: 0;
  }
  .ranking li {
    display: flex;
    justify-content: space-between;
    gap: 10px;
  }
  .confirmation-actions {
    display: flex;
    justify-content: flex-end;
    gap: 9px;
    margin-top: 20px;
  }
  @media (max-width: 1000px) {
    .gz-workspace {
      grid-template-columns: minmax(0, 1fr);
    }
    .gz-context .gz-items {
      grid-template-columns: repeat(5, minmax(0, 1fr));
    }
    .session-columns {
      grid-template-columns: minmax(0, 1fr);
      gap: 10px;
    }
  }
  @media (max-width: 550px) {
    .gz-tabs {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      width: 100%;
    }
    .gz-tabs button {
      padding: 9px 6px;
      font-size: 11px;
      gap: 5px;
    }
    .gz-tabs :global(svg) {
      width: 15px;
    }
    .gz-summary {
      gap: 9px 15px;
      font-size: 11px;
    }
    .header-actions button {
      font-size: 11px;
    }
    .gz-context .gz-items {
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }
    .item-search input,
    .give-form input,
    .modal input {
      font-size: 16px;
    }
    .person-heading h2 {
      font-size: 13px;
    }
    .gz-map {
      max-height: 420px;
    }
    .survivor-chip {
      flex-basis: 100%;
    }
    .survivor-chip .bite-dot {
      margin-left: auto;
    }
  }
</style>
