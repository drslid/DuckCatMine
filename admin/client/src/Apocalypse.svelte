<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from './api';
  import Icon from './Icon.svelte';
  let {
    toast,
    openPlayer,
  }: {
    toast: (message: string, error?: boolean) => void;
    openPlayer: (name: string) => void;
  } = $props();
  let status = $state<any>(null),
    busy = $state(''),
    threatValue = $state(''),
    mapSize = $state(640),
    report = $state<string[]>([]);
  const phases: Record<string, string> = {
    calm: 'Calme',
    signs: 'Indices',
    pressure: 'Pression',
    peak: 'Pic : horde',
    relief: 'Répit',
  };
  let infected = $derived(
    Object.entries((status?.infected || {}) as Record<string, number>).sort(
      (a, b) => b[1] - a[1],
    ),
  );
  let survivors = $derived<any[]>(status?.survivors || []);
  let map = $derived(status?.map);
  let open = $derived(!!status?.enabled && !status?.stale);

  async function load() {
    try {
      status = await api('/api/apocalypse');
    } catch (e) {
      toast(e.message, true);
    }
  }
  onMount(() => {
    load();
    const timer = setInterval(() => {
      if (!document.hidden) load();
    }, 5000);
    return () => clearInterval(timer);
  });
  function lastLine(text: string) {
    return (
      String(text || '')
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean)
        .at(-1) || ''
    );
  }
  async function act(action: string, value?: any, question?: string) {
    if (question && !confirm(question)) return;
    busy = action;
    try {
      const result = await api('/api/apocalypse/action', 'POST', {
        action,
        value,
      });
      if (action === 'selftest')
        report = String(result.result || '')
          .split('\n')
          .map((line) => line.trim())
          .filter(Boolean);
      else toast(lastLine(result.result) || 'C’est fait.');
      if (action === 'threat') threatValue = '';
      setTimeout(load, 700);
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = '';
    }
  }
  function duration(seconds: number) {
    const s = Math.max(0, Math.floor(seconds || 0));
    const h = Math.floor(s / 3600),
      m = Math.floor((s % 3600) / 60);
    return h
      ? `${h} h ${String(m).padStart(2, '0')}`
      : `${m} min ${String(s % 60).padStart(2, '0')}`;
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
</script>

<div class="section-heading">
  <div>
    <p class="eyebrow">MODE SURVIE ZOMBIE</p>
    <h1>Ground Zero</h1>
  </div>
  {#if status?.installed}<span class="pill" class:alive={open}
      >{status.stale
        ? 'Serveur arrêté'
        : status.enabled
          ? 'Ouvert'
          : 'Fermé'}</span
    >{/if}
</div>

{#if !status}<p class="muted">Lecture de l’état du scénario…</p>
{:else if !status.installed}<article class="card empty large">
    <span class="action-icon"><Icon name="skull" size={30} /></span>
    <h2>Le module Ground Zero n’est pas installé.</h2>
    <p>
      Installe DuckCatApocalypse (voir son README), crée le monde avec
      <code>/apocalypse admin world create</code>, puis reviens ici.
    </p>
  </article>
{:else}
  {#if status.stale}<p class="notice">
      Aucune nouvelle du serveur depuis {duration(status.ageSeconds)} : il est arrêté
      ou en cours de redémarrage. Les valeurs ci-dessous sont les dernières connues.
    </p>{/if}
  {#if status.problem || status.configProblem}<p class="notice error">
      {status.problem || status.configProblem}
    </p>{/if}
  <div class="gz-grid">
    <article class="card">
      <div class="card-heading">
        <h2>Partie</h2>
        {#if status.enabled}<button
            class="danger"
            disabled={!!busy || status.stale}
            onclick={() =>
              act(
                'disable',
                undefined,
                'Fermer Ground Zero ? Les survivants présents retrouvent leur survie habituelle ; tout est conservé et les horloges sont figées.',
              )}>Fermer</button
          >{:else}<button
            class="primary"
            disabled={!!busy || status.stale}
            onclick={() => act('enable')}>Ouvrir</button
          >{/if}
      </div>
      <dl class="key-values">
        <dt>Monde</dt>
        <dd>{status.world || '—'} (génération {status.epoch ?? '—'})</dd>
        <dt>Jour</dt>
        <dd>
          {status.time
            ? `${status.time.day + 1}, ${status.time.night ? 'nuit' : 'jour'}`
            : '—'}
        </dd>
        <dt>Survivants présents</dt>
        <dd>{survivors.length}</dd>
        <dt>Morsures en cours</dt>
        <dd>{status.activeInfections ?? 0}</dd>
        <dt>Sacs au sol</dt>
        <dd>{status.activeBags ?? 0}</dd>
        <dt>Base du scénario</dt>
        <dd>{status.database === false ? 'EN ERREUR' : 'OK'}</dd>
        {#if status.classicProfilesHeld > 0}<dt>
            Inventaires classiques gardés
          </dt>
          <dd>{status.classicProfilesHeld}</dd>{/if}
      </dl>
    </article>

    <article class="card">
      <div class="card-heading">
        <h2>Menace</h2>
        <span class="pill phase" data-phase={status.threat?.phase}
          >{phases[status.threat?.phase] ||
            status.threat?.phaseLabel ||
            '—'}</span
        >
      </div>
      <p class="threat-level">
        {Number(status.threat?.level || 1).toFixed(1)}<small> / 50</small>
      </p>
      <meter
        min="1"
        max="10"
        low="3"
        high="6"
        optimum="1"
        value={Math.min(10, status.threat?.level || 1)}
      ></meter>
      <p class="muted small">
        Phase suivante dans {duration(status.threat?.secondsLeft)} · {status
          .threat?.hordes ?? 0}
        horde(s) depuis le démarrage. La menace monte seulement quand quelqu’un joue.
      </p>
      <form
        class="inline threat-form"
        onsubmit={(e) => {
          e.preventDefault();
          act('threat', Number(String(threatValue).replace(',', '.')));
        }}
      >
        <input
          type="number"
          min="1"
          max="50"
          step="0.5"
          placeholder="1 à 50"
          aria-label="Nouvelle menace"
          bind:value={threatValue}
          required
        /><button class="secondary" disabled={!!busy || status.stale}
          >Fixer</button
        >
      </form>
    </article>

    <article class="card">
      <h2>Règles</h2>
      <div class="rule">
        <div>
          <strong>PvP</strong>
          <small class="muted"
            >Jamais au refuge ni entre coéquipiers. Annoncé aux survivants.</small
          >
        </div>
        <button
          class="toggle"
          class:on={status.rules?.pvp}
          role="switch"
          aria-checked={!!status.rules?.pvp}
          aria-label="PvP"
          disabled={!!busy || status.stale}
          onclick={() =>
            act(
              'pvp',
              !status.rules?.pvp,
              status.rules?.pvp
                ? undefined
                : 'Activer le PvP dans Ground Zero ?',
            )}><i></i></button
        >
      </div>
      <div class="rule">
        <div>
          <strong>Destruction de blocs</strong>
          <small class="muted"
            >Les infectés bloqués enfoncent portes, vitres et murs construits.</small
          >
        </div>
        <button
          class="toggle"
          class:on={status.rules?.destruction}
          role="switch"
          aria-checked={!!status.rules?.destruction}
          aria-label="Destruction de blocs"
          disabled={!!busy || status.stale}
          onclick={() => act('destruction', !status.rules?.destruction)}
          ><i></i></button
        >
      </div>
      <p class="muted small">
        {status.damagedBlocks || 0} bloc(s) entamé(s) · {status.incubatingAnimals ||
          0}
        animal(aux) en incubation
      </p>
    </article>

    <article class="card wide">
      <div class="card-heading">
        <h2>Survivants</h2>
        <span class="pill">{survivors.length} en jeu</span>
      </div>
      {#if survivors.length}<div class="survivors">
          {#each survivors as s (s.name)}<div
              class="survivor"
              class:bitten={s.infectionSeconds != null}
            >
              <button class="name" onclick={() => openPlayer(s.name)}
                ><span class="account-avatar">{s.name.slice(0, 2)}</span><span
                  ><strong>{s.name}</strong><small
                    >{s.inRefuge ? 'Au refuge' : s.sector || '—'}{s.team
                      ? ` · ${s.team}`
                      : ''}</small
                  ></span
                ></button
              >
              <span class="fact"
                ><small>Vie</small>{duration(s.lifeSeconds)}</span
              >
              <span class="fact"><small>Points</small>{s.points ?? 0}</span>
              <span class="fact"><small>Santé</small>{s.health ?? '—'}</span>
              {#if s.infectionSeconds != null}<span class="badge"
                  >MORDU · {s.infectionSeconds} s</span
                >{/if}
              <button
                class="quiet"
                disabled={!!busy || s.inRefuge || status.stale}
                onclick={() =>
                  act('horde', s.name, `Envoyer une horde vers ${s.name} ?`)}
                >Horde</button
              >
            </div>{/each}
        </div>{:else}<p class="muted">
          Personne dans Ground Zero pour l’instant.
        </p>{/if}
      {#if status.teams?.length}<h3>Équipes</h3>
        <ul class="teams">
          {#each status.teams as team (team.name)}<li>
              <strong>{team.name}</strong>
              <span class="muted">{team.members.join(', ')}</span>
            </li>{/each}
        </ul>{/if}
    </article>

    <article class="card">
      <div class="card-heading">
        <h2>Infectés</h2>
        <span class="pill"
          >{status.infectedTotal || 0} / {status.rules?.globalCap ?? '—'}</span
        >
      </div>
      {#if infected.length}<ul class="bars">
          {#each infected as [id, count] (id)}<li>
              <span>{status.archetypeLabels?.[id] || id}</span>
              <meter min="0" max={Math.max(1, infected[0][1])} value={count}
              ></meter>
              <b>{count}</b>
            </li>{/each}
        </ul>{:else}<p class="muted">
          Aucun infecté actif : ils apparaissent autour des survivants.
        </p>{/if}
    </article>

    <article class="card wide">
      <div class="card-heading wrap-heading">
        <h2>Carte</h2>
        <form
          class="inline map-tools"
          onsubmit={(e) => {
            e.preventDefault();
            act('map', mapSize);
          }}
        >
          <select bind:value={mapSize} aria-label="Taille de la carte">
            <option value={320}>Centre (640 blocs)</option>
            <option value={640}>Ville (1 280 blocs)</option>
            <option value={960}>Ville et abords</option>
            <option value={1280}>Presque tout</option>
          </select><button class="secondary" disabled={!!busy || status.stale}
            ><Icon name="refresh" />Redessiner</button
          >
        </form>
      </div>
      {#if map && status.mapRenderedAt}<svg
          class="gz-map"
          viewBox={`0 0 ${map.size} ${map.size}`}
          role="img"
          aria-label="Carte de Ground Zero avec les survivants"
          ><image
            href={`/api/apocalypse/map.png?v=${Math.round(status.mapRenderedAt)}`}
            width={map.size}
            height={map.size}
          />{#if status.refuge}<circle
              class="refuge"
              cx={status.refuge.x - map.minX}
              cy={status.refuge.z - map.minZ}
              r={Math.max(status.refuge.radius, map.size / 120)}
            />{/if}{#each survivors.filter( (s) => onMap(s.x, s.z) ) as s (s.name)}<g
              class="dot"
              class:bitten={s.infectionSeconds != null}
              transform={`translate(${s.x - map.minX} ${s.z - map.minZ})`}
              ><circle r={map.size / 90} /><text
                y={-map.size / 60}
                font-size={map.size / 40}>{s.name}</text
              ></g
            >{/each}</svg
        >
        <p class="muted small">
          Rendu {new Date(status.mapRenderedAt).toLocaleString('fr-FR')} · {map.size}
          × {map.size} blocs, nord en haut. Les points se mettent à jour toutes les
          5 s ; le fond, seulement quand tu le redessines (quelques minutes pour une
          grande carte).
        </p>{:else}<p class="muted">
          Pas encore de carte : choisis une taille puis « Redessiner ».
        </p>{/if}
    </article>

    <article class="card">
      <h2>Maintenance</h2>
      <p class="muted small">
        L’autotest vérifie objets, armes, base, monde, infectés, siège, faune,
        largage et fouille sur le serveur réel, sans joueur.
      </p>
      <div class="inline wrap">
        <button
          class="secondary"
          disabled={!!busy || status.stale}
          onclick={() => act('selftest')}
          ><Icon name="check" />{busy === 'selftest'
            ? 'Autotest…'
            : 'Autotest'}</button
        ><button
          class="quiet"
          disabled={!!busy || status.stale}
          onclick={() => act('reload')}>Recharger la configuration</button
        >
      </div>
      {#if report.length}<ul class="report">
          {#each report as line, index (index)}<li
              class:failed={line.startsWith('ÉCHEC')}
              class:passed={line.startsWith('OK')}
            >
              {line}
            </li>{/each}
        </ul>{/if}
    </article>
  </div>
{/if}

<style>
  .gz-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 18px;
  }
  .gz-grid .wide {
    grid-column: span 2;
  }
  @media (max-width: 1150px) {
    .gz-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
  @media (max-width: 760px) {
    .gz-grid {
      grid-template-columns: minmax(0, 1fr);
    }
    .gz-grid .wide {
      grid-column: auto;
    }
  }
  .pill.alive {
    color: var(--accent);
    border-color: var(--accent);
  }
  .notice {
    border: 1px solid var(--border);
    background: var(--surface2);
    border-radius: 12px;
    padding: 12px 16px;
    margin: 0 0 18px;
    color: var(--soft);
  }
  .notice.error {
    color: #f3ab99;
    border-color: #804b3e;
  }
  .threat-level {
    font-size: 42px;
    font-weight: 750;
    margin: 4px 0 8px;
    color: var(--text);
  }
  .threat-level small {
    font-size: 14px;
    color: var(--muted);
    font-weight: 500;
  }
  meter {
    width: 100%;
    height: 10px;
  }
  .small {
    font-size: 11px;
  }
  .pill.phase[data-phase='peak'],
  .pill.phase[data-phase='pressure'] {
    color: #f3ab99;
    border-color: #804b3e;
  }
  .pill.phase[data-phase='relief'] {
    color: var(--accent);
  }
  .rule {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 12px 0;
    border-bottom: 1px solid var(--border);
  }
  .rule div {
    display: grid;
    gap: 3px;
  }
  .toggle {
    width: 46px;
    height: 26px;
    border-radius: 20px;
    padding: 3px;
    display: flex;
    justify-content: flex-start;
    align-items: center;
    min-height: 0;
    background: var(--surface3);
    border-color: var(--border);
    flex-shrink: 0;
  }
  .toggle i {
    display: block;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--muted);
    transition: transform 0.15s;
  }
  .toggle.on {
    background: #4d6a3b;
    border-color: var(--accent);
  }
  .toggle.on i {
    transform: translateX(18px);
    background: var(--accent);
  }
  .survivors {
    display: grid;
    gap: 8px;
  }
  .survivor {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    padding: 10px 12px;
    border: 1px solid var(--border);
    border-radius: 12px;
    background: var(--surface2);
  }
  .survivor.bitten {
    border-color: #804b3e;
  }
  .survivor .name {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-right: auto;
    text-align: left;
    padding: 0;
    border: 0;
    background: none;
  }
  .survivor .name span:last-child {
    display: grid;
  }
  .survivor small {
    color: var(--muted);
    font-size: 10px;
  }
  .fact {
    display: grid;
    font-size: 12px;
    min-width: 54px;
  }
  .teams {
    list-style: none;
    padding: 0;
    margin: 6px 0 0;
    display: grid;
    gap: 6px;
    font-size: 12px;
  }
  h3 {
    margin: 18px 0 4px;
    font-size: 13px;
  }
  .bars {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 9px;
  }
  .bars li {
    display: grid;
    grid-template-columns: 110px minmax(0, 1fr) 28px;
    align-items: center;
    gap: 10px;
    font-size: 12px;
  }
  .bars b {
    text-align: right;
  }
  .gz-map {
    width: 100%;
    height: auto;
    border-radius: 12px;
    border: 1px solid var(--border);
    background: #101810;
    display: block;
  }
  .gz-map .refuge {
    fill: none;
    stroke: #c0dd96;
    stroke-width: 2;
  }
  .gz-map .dot circle {
    fill: #ffe08a;
    stroke: #19261b;
    stroke-width: 1;
  }
  .gz-map .dot.bitten circle {
    fill: #ff6b5a;
  }
  .gz-map .dot text {
    fill: #ffffff;
    text-anchor: middle;
    paint-order: stroke;
    stroke: #000000;
    stroke-width: 2px;
    font-weight: 700;
  }
  .report {
    list-style: none;
    padding: 0;
    margin: 14px 0 0;
    display: grid;
    gap: 5px;
    font-size: 11px;
    max-height: 320px;
    overflow: auto;
  }
  .report li.passed {
    color: var(--soft);
  }
  .report li.failed {
    color: #f3ab99;
    font-weight: 700;
  }
  select {
    max-width: 180px;
    min-width: 0;
  }
  .wrap-heading {
    flex-wrap: wrap;
  }
  .map-tools {
    flex-wrap: nowrap;
    justify-content: flex-end;
    min-width: 0;
  }
  @media (max-width: 760px) {
    .survivor .name {
      flex-basis: 100%;
    }
    .survivor > button.quiet {
      margin-left: auto;
    }
  }
  .threat-form input {
    min-width: 0;
    width: 100%;
  }
</style>
