<script lang="ts">
  import Icon from './Icon.svelte';
  import { asset } from './api';
  let { status, busy, act, ask }: any = $props();
  let draft = $state<Record<string, any>>({}),
    dirty = $state<Record<string, boolean>>({});
  let threat = $state<number | undefined>();
  const bestiary = [
    {id:'spitter',name:'Cracheur d’acide',note:'Repère la charge verte, puis esquive le crachat. La flaque se dissipe après 3 s.'},
    {id:'jumper',name:'Bondisseur',note:'Son élan annonce un saut de plus de 3 blocs. Frappe-le pendant la préparation pour l’interrompre.'},
    {id:'crawler',name:'Rampant',note:'Très bas, il peut se glisser sous les passages d’un bloc. Garde un œil au sol.'},
    {id:'stalker',name:'Traqueur décharné',note:'Silencieux à l’approche. Son cri annonce une courte accélération, suivie d’une récupération.'},
    {id:'bloated',name:'Boursouflé',note:'Recule après son élimination : son nuage toxique reste 4 s, sans abîmer les constructions.'},
    {id:'breaker',name:'Briseur',note:'Deux fois plus efficace contre les murs et les barricades. Lent, robuste, et très reconnaissable.'},
  ];
  let groups = $derived([
    ...new Set((status.settingsCatalog || []).map((s: any) => s.group)),
  ] as string[]);
  $effect(() => {
    for (const [key, value] of Object.entries(status.settings || {}))
      if (!dirty[key]) draft[key] = value;
  });
  async function save(key: string) {
    if (await act('set', { key, value: draft[key] })) dirty[key] = false;
  }
</script>

<div class="rules-layout">
  <article class="card">
    <div class="card-heading">
      <h2>Le rythme de votre partie</h2>
      <span class="pill">En direct</span>
    </div>
    <p class="muted">
      Choisis une ambiance, puis ajuste seulement ce qui vous intéresse.
    </p>
    <div class="presets">
      {#each Object.entries(status.presets || {}) as [id, preset]}
        <button
          class="preset"
          disabled={!!busy || status.stale}
          onclick={() =>
            ask({
              action: 'preset',
              value: id,
              title: `Difficulté ${(preset as any).label}`,
              description:
                'Les réglages de ce préréglage sont appliqués immédiatement. Les autres options restent à leur valeur actuelle.',
            })}
        >
          <Icon
            name={id === 'relaxed'
              ? 'sun'
              : id === 'nightmare'
                ? 'skull'
                : 'shield'}
            size={24}
          />
          <strong>{(preset as any).label}</strong><small
            >{id === 'relaxed'
              ? 'Explorer et construire'
              : id === 'nightmare'
                ? 'La ville ne pardonne pas'
                : 'Survivre ensemble'}</small
          >
        </button>
      {/each}
    </div>
    <p class="notice">
      Les morsures et les sacs déjà en cours gardent leur échéance. Les
      nouvelles durées concernent les prochains événements.
    </p>
  </article>

  <article class="card">
    <div class="card-heading">
      <h2>Menace</h2>
      <span class="pill"
        >{Number(status.threat?.level || 1).toFixed(1)} / 50</span
      >
    </div>
    <p class="muted">Une valeur élevée augmente la pression des infectés.</p>
    <form
      class="inline"
      onsubmit={(e) => {
        e.preventDefault();
        act('threat', threat);
      }}
    >
      <label
        >Niveau<input
          type="number"
          min="1"
          max="50"
          step="0.1"
          placeholder="1 à 50"
          bind:value={threat}
          required
        /></label
      >
      <button class="secondary" disabled={!!busy || status.stale}
        ><Icon name="check" />Appliquer</button
      >
    </form>
    {#each [{ key: 'pvp', label: 'Combat entre joueurs', icon: 'users', note: 'Toujours interdit au refuge et entre coéquipiers.' }, { key: 'destruction', label: 'Destruction par les infectés', icon: 'build', note: 'Les infectés peuvent enfoncer portes, vitres et murs construits.' }] as rule}
      <div class="rule">
        <div>
          <strong><Icon name={rule.icon} size={16} />{rule.label}</strong><small
            class="muted">{rule.note}</small
          >
        </div>
        <button
          class="toggle"
          class:on={status.rules?.[rule.key]}
          role="switch"
          aria-checked={!!status.rules?.[rule.key]}
          aria-label={rule.label}
          disabled={!!busy || status.stale}
          onclick={() =>
            ask({
              action: rule.key,
              value: !status.rules?.[rule.key],
              title: rule.label,
              description: `${status.rules?.[rule.key] ? 'Désactiver' : 'Activer'} cette règle immédiatement ? Les survivants seront informés.`,
            })}><i></i></button
        >
      </div>
    {/each}
  </article>

  {#if status.capabilities?.includes('horror-v1')}
    <details class="card bestiary">
      <summary><Icon name="skull" size={18} /><strong>Connaître les infectés</strong><span>6 profils à repérer</span></summary>
      <p class="muted">Ils apparaissent à au moins {status.rules?.minSpawnDistance || 150} blocs de tous les joueurs, à l’extérieur. Le sonar les repère à 100 blocs.</p>
      <div class="bestiary-grid">{#each bestiary as enemy}<article><img src={asset(`horror/${enemy.id}.svg`)} alt={enemy.name} width="84" height="84" loading="lazy" /><div><strong>{enemy.name}</strong><p>{enemy.note}</p></div></article>{/each}</div>
      <p class="muted">Pack facultatif d’environ 100 Ko, proposé dans Minecraft : modèles et sons, sans mod. Si un joueur ne l’a pas chargé, les infectés gardent leur apparence native et les mêmes capacités.</p>
    </details>
  {/if}

  <article class="card detailed">
    <div class="card-heading">
      <h2>Réglages à la carte</h2>
      <span class="muted small">Enregistrés pour les prochaines sessions</span>
    </div>
    {#if !groups.length}<p class="notice">
        Le plugin doit publier son catalogue de réglages pour les afficher ici.
      </p>{/if}
    {#each groups as group, index}
      <details class="setting-group" open={index === 0}>
        <summary
          ><Icon
            name={group === 'Infection'
              ? 'heart'
              : group === 'Monde'
                ? 'map'
                : group === 'Faune'
                  ? 'paw'
                  : 'skull'}
            size={18}
          />{group}
          <span class="muted"
            >{status.settingsCatalog.filter((s: any) => s.group === group)
              .length}</span
          ></summary
        >
        <div class="setting-list">
          {#each status.settingsCatalog.filter((s: any) => s.group === group) as setting (setting.key)}
            <form
              class="setting-row"
              onsubmit={(e) => {
                e.preventDefault();
                save(setting.key);
              }}
            >
              <label for={`gz-${setting.key}`}
                ><strong>{setting.label}</strong><small class="muted"
                  >{setting.kind === 'boolean'
                    ? 'Activer ou désactiver'
                    : `${setting.min} à ${setting.max}`}</small
                ></label
              >
              {#if setting.kind === 'boolean'}
                <select
                  id={`gz-${setting.key}`}
                  bind:value={draft[setting.key]}
                  onchange={() => (dirty[setting.key] = true)}
                  disabled={!!busy || status.stale}
                >
                  <option value={true}>Activé</option><option value={false}
                    >Désactivé</option
                  >
                </select>
              {:else}
                <input
                  id={`gz-${setting.key}`}
                  type="number"
                  min={setting.min}
                  max={setting.max}
                  step={setting.kind === 'integer' ? 1 : 'any'}
                  bind:value={draft[setting.key]}
                  oninput={() => (dirty[setting.key] = true)}
                  required
                  disabled={!!busy || status.stale}
                />
              {/if}
              <button
                class="secondary"
                disabled={!!busy || status.stale || !dirty[setting.key]}
                aria-label={`Enregistrer : ${setting.label}`}
                ><Icon name="check" size={16} /><span>Enregistrer</span></button
              >
            </form>
          {/each}
        </div>
      </details>
    {/each}
  </article>
</div>

<style>
  .bestiary { grid-column:1/-1; }
  .bestiary > summary { display:flex;align-items:center;gap:9px;cursor:pointer;min-height:32px; }
  .bestiary > summary span { color:var(--muted);font-size:11px;margin-left:auto; }
  .bestiary-grid { display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,280px),1fr));gap:10px;margin:12px 0; }
  .bestiary-grid article { display:flex;align-items:center;gap:10px;min-width:0;background:var(--surface2);border-radius:10px;padding:8px; }
  .bestiary-grid img { flex:none;image-rendering:pixelated; }
  .bestiary-grid strong { font-size:12px; }
  .bestiary-grid p { font-size:11px;line-height:1.5;color:var(--muted);margin:5px 0 0; }
  .rules-layout {
    display: grid;
    grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
    gap: 14px;
  }
  .detailed {
    grid-column: 1/-1;
  }
  .presets {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 9px;
    margin: 16px 0;
  }
  .preset {
    display: grid;
    justify-items: start;
    text-align: left;
    gap: 6px;
    padding: 14px;
    background: var(--surface2);
  }
  .preset strong {
    font-size: 13px;
  }
  .preset small {
    color: var(--muted);
    font-size: 11px;
    line-height: 1.4;
  }
  .preset :global(svg) {
    color: var(--accent);
  }
  .notice {
    padding: 11px 13px;
    border-radius: 10px;
    background: var(--surface2);
    font-size: 12px;
    line-height: 1.6;
    color: var(--soft);
  }
  .small {
    font-size: 11px;
  }
  .rule {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 14px;
    padding: 15px 0;
    border-top: 1px solid var(--border);
  }
  .rule strong {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 12px;
  }
  .rule small {
    display: block;
    font-size: 11px;
    margin-top: 5px;
  }
  .toggle {
    flex-shrink: 0;
    width: 44px;
    height: 26px;
    min-height: 0;
    border-radius: 20px;
    padding: 3px;
    display: flex;
    background: var(--surface3);
  }
  .toggle i {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--muted);
  }
  .toggle.on {
    background: #4d6a3b;
  }
  .toggle.on i {
    transform: translateX(18px);
    background: var(--accent);
  }
  .inline {
    align-items: end;
    margin: 14px 0;
  }
  .inline label {
    min-width: 0;
    flex: 1;
  }
  .inline input {
    width: 100%;
  }
  .setting-group {
    border-top: 1px solid var(--border);
  }
  .setting-group summary {
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 16px 0;
    font-size: 13px;
    font-weight: 650;
  }
  .setting-group summary span {
    margin-left: auto;
  }
  .setting-list {
    display: grid;
    gap: 8px;
    padding-bottom: 12px;
  }
  .setting-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 140px 128px;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-radius: 10px;
    background: var(--surface2);
  }
  .setting-row label {
    margin: 0;
  }
  .setting-row strong {
    font-weight: 550;
    font-size: 12px;
  }
  .setting-row small {
    font-size: 10px;
    margin-top: 3px;
  }
  .setting-row input,
  .setting-row select {
    width: 100%;
    min-width: 0;
  }
  .setting-row button {
    justify-content: center;
  }
  @media (max-width: 900px) {
    .rules-layout {
      grid-template-columns: minmax(0, 1fr);
    }
    .detailed {
      grid-column: auto;
    }
  }
  @media (max-width: 550px) {
    .presets {
      gap: 6px;
    }
    .preset {
      padding: 10px;
    }
    .preset small {
      font-size: 10px;
    }
    .setting-row {
      grid-template-columns: minmax(0, 1fr) 44px;
      gap: 8px;
    }
    .setting-row label {
      grid-column: 1/-1;
    }
    .setting-row button span {
      display: none;
    }
    .setting-row input,
    .setting-row select,
    .inline input {
      font-size: 16px;
    }
    .card-heading {
      flex-wrap: wrap;
    }
  }
</style>
