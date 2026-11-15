<script lang="ts">
  import { onMount } from 'svelte';
  import { asset } from './api';
  import Icon from './Icon.svelte';
  import { monsters } from './bestiary';
  let { status, busy, act }: any = $props();
  let selected = $state('spitter');
  let picker: HTMLElement;
  let draft = $state<Record<string, any>>({}),
    dirty = $state<Record<string, boolean>>({});
  let kinds = $derived(
    (status.archetypes || []).map((a: any) => a.id) as string[],
  );
  let info = $derived(
    monsters[selected] || { name: selected, role: 'Infecté', note: '' },
  );
  let fields = $derived(
    (status.settingsCatalog || []).filter((s: any) =>
      s.key.startsWith(`mob-${selected}-`),
    ),
  );
  const basics = ['health', 'damage', 'speed', 'spawn-weight'];
  let basic = $derived(
    fields.filter((s: any) =>
      basics.includes(s.key.slice(`mob-${selected}-`.length)),
    ),
  );
  let advanced = $derived(fields.filter((s: any) => !basic.includes(s)));
  $effect(() => {
    for (const [key, value] of Object.entries(status.settings || {}))
      if (!dirty[key]) draft[key] = value;
  });
  function centerChoice() {
    const button = picker?.querySelector('[aria-pressed="true"]');
    if (button && picker.scrollWidth > picker.clientWidth) {
      const a = button.getBoundingClientRect(),
        b = picker.getBoundingClientRect();
      picker.scrollLeft += a.left - b.left - (picker.clientWidth - a.width) / 2;
    }
  }
  $effect(() => {
    selected;
    requestAnimationFrame(centerChoice);
  });
  onMount(() => {
    const observer = new ResizeObserver(centerChoice);
    observer.observe(picker);
    return () => observer.disconnect();
  });
  async function save(key: string) {
    if (await act('set', { key, value: draft[key] })) dirty[key] = false;
  }
</script>

<div class="bestiary-heading">
  <div>
    <h2>Le bestiaire de Ground Zero</h2>
    <p class="muted">Choisis une créature, façonne ta prochaine rencontre.</p>
  </div>
  <span class="pill">En direct</span>
</div>
<p class="muted bestiary-hint">
  Apparitions à plus de 100 et moins de 200 blocs. Les infectés sont retirés
  quand aucun joueur n’est à moins de 200 blocs. Certaines pièces abandonnées
  sont occupées ; les lieux visités et les bases protégées restent à l’abri des
  réapparitions.
</p>
{#if !status.capabilities?.includes('infected-tuning')}<p class="notice">
    Le nouveau plugin doit être activé pour modifier le bestiaire.
  </p>{/if}
<div class="bestiary-layout">
  <nav
    class="creature-picker"
    bind:this={picker}
    aria-label="Choisir un type d’infecté"
  >
    {#each kinds as id}<button
        class:chosen={selected === id}
        aria-pressed={selected === id}
        onclick={() => (selected = id)}
      >
        {#if monsters[id]?.model}<img
            src={asset(`horror/${id}.svg`)}
            alt=""
            width="58"
            height="58"
            loading="lazy"
          />{:else}<span class="native-portrait"
            ><Icon
              name={id.startsWith('infected_') ? 'paw' : 'skull'}
              size={30}
            /></span
          >{/if}
        <span
          ><strong
            >{status.archetypeLabels?.[id] || monsters[id]?.name || id}</strong
          ><small
            >{monsters[id]?.role || 'Infecté'} · {status.infected?.[id] || 0} présents</small
          ></span
        >
      </button>{/each}
  </nav>
  <article class="card creature-detail">
    <div class="creature-hero">
      {#if info.model}<img
          src={asset(`horror/${selected}.svg`)}
          alt={info.name}
          width="128"
          height="128"
        />{:else}<span class="native-portrait large"
          ><Icon
            name={selected.startsWith('infected_') ? 'paw' : 'skull'}
            size={54}
          /></span
        >{/if}
      <div>
        <span class="eyebrow">{info.role}</span>
        <h2>{status.archetypeLabels?.[selected] || info.name}</h2>
        <p class="muted">{info.note}</p>
      </div>
    </div>
    <p class="live-note">
      <Icon name="check" size={16} />Vie, dégâts, vitesse et résistance changent
      immédiatement. La vie restante garde la même proportion. Fréquence et
      plafonds concernent les prochaines apparitions.
    </p>
    <div class="creature-fields">
      {#each basic as field (field.key)}{@render input(field)}{/each}
    </div>
    <details class="creature-advanced">
      <summary
        >Résistance et capacités spéciales <Icon
          name="chevron"
          size={16}
        /></summary
      >
      <div class="creature-fields">
        {#each advanced as field (field.key)}{@render input(field)}{/each}
      </div>
    </details>
    {#if !fields.length}<p class="notice">
        Le serveur n’a pas encore publié les réglages de cette créature.
      </p>{/if}
  </article>
</div>

{#snippet input(field: any)}
  <form
    onsubmit={(e) => {
      e.preventDefault();
      save(field.key);
    }}
  >
    <label for={`bestiary-${field.key}`}>{field.label}</label>
    <div class="field-controls">
      <input
        id={`bestiary-${field.key}`}
        type="number"
        min={field.min}
        max={field.max}
        step={field.kind === 'integer' ? 1 : 'any'}
        bind:value={draft[field.key]}
        oninput={() => (dirty[field.key] = true)}
        disabled={!!busy || status.stale}
        required
      />
      <button
        class="secondary"
        disabled={!!busy || status.stale || !dirty[field.key]}
        aria-label={`Appliquer : ${field.label}`}
        title="Appliquer immédiatement"><Icon name="check" size={18} /></button
      >
    </div>
    <small class="muted">{field.min} à {field.max}</small>
  </form>
{/snippet}

<style>
  .bestiary-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    margin: 20px 0 8px;
  }
  .bestiary-heading h2 {
    margin: 0;
  }
  .bestiary-heading p {
    margin-bottom: 0;
  }
  .bestiary-hint {
    max-width: 850px;
    font-size: 12px;
    line-height: 1.7;
    margin: 14px 0 24px;
  }
  .bestiary-layout {
    display: grid;
    grid-template-columns: 238px minmax(0, 1fr);
    gap: 20px;
    align-items: start;
  }
  .creature-picker {
    display: grid;
    gap: 7px;
    max-height: 730px;
    overflow: auto;
    padding-right: 3px;
  }
  .creature-picker button {
    width: 100%;
    display: flex;
    gap: 9px;
    align-items: center;
    text-align: left;
    padding: 8px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    color: var(--text);
  }
  .creature-picker button.chosen {
    background: var(--surface2);
    border-color: var(--accent);
  }
  .creature-picker button > span:last-child {
    min-width: 0;
    display: grid;
    gap: 5px;
  }
  .creature-picker strong {
    font-size: 12px;
  }
  .creature-picker small {
    font-size: 10px;
    color: var(--muted);
  }
  .creature-picker img {
    flex: none;
  }
  .native-portrait {
    width: 58px;
    height: 58px;
    flex: none;
    display: grid;
    place-items: center;
    color: var(--accent);
    background: var(--surface2);
    border-radius: 10px;
  }
  .native-portrait.large {
    width: 128px;
    height: 128px;
  }
  .creature-hero {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-bottom: 12px;
  }
  .creature-hero img {
    flex: none;
  }
  .creature-hero h2 {
    margin: 6px 0 10px;
  }
  .creature-hero p {
    font-size: 12px;
    line-height: 1.65;
    margin: 0;
  }
  .live-note {
    font-size: 11px;
    line-height: 1.7;
    color: var(--muted);
    border-left: 2px solid var(--accent);
    padding: 10px 13px;
    margin: 20px 0;
  }
  .live-note :global(svg) {
    vertical-align: middle;
    color: var(--accent);
    margin-right: 5px;
  }
  .creature-fields {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px 22px;
  }
  .creature-fields form {
    min-width: 0;
  }
  .creature-fields label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    margin-bottom: 8px;
  }
  .field-controls {
    display: flex;
    gap: 7px;
  }
  .field-controls input {
    min-width: 0;
    width: 100%;
    font-size: 16px;
  }
  .field-controls button {
    padding: 8px 12px;
    flex: none;
  }
  .creature-fields small {
    display: block;
    font-size: 10px;
    margin-top: 5px;
  }
  .creature-advanced {
    margin-top: 24px;
    border-top: 1px solid var(--border);
    padding-top: 17px;
  }
  .creature-advanced summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    cursor: pointer;
    font-size: 12px;
    font-weight: 600;
    list-style: none;
  }
  .creature-advanced .creature-fields {
    margin-top: 22px;
  }
  @media (max-width: 850px) {
    .bestiary-layout {
      grid-template-columns: 1fr;
    }
    .creature-picker {
      display: flex;
      max-width: 100%;
      overflow-x: auto;
      scroll-snap-type: x proximity;
      padding: 0 0 9px;
      scrollbar-width: none;
    }
    .creature-picker::-webkit-scrollbar {
      display: none;
    }
    .creature-picker button {
      flex: 0 0 190px;
      scroll-snap-align: start;
    }
    .creature-detail {
      min-width: 0;
    }
    .creature-hero {
      gap: 12px;
    }
    .creature-hero img,
    .native-portrait.large {
      width: 92px;
      height: 92px;
    }
    .creature-hero h2 {
      font-size: 18px;
    }
    .creature-hero p {
      font-size: 11px;
    }
    .creature-fields {
      gap: 16px;
    }
    .bestiary-hint {
      font-size: 11px;
    }
  }
  @media (max-width: 450px) {
    .creature-fields {
      grid-template-columns: 1fr;
    }
    .bestiary-heading {
      align-items: flex-start;
    }
    .bestiary-heading h2 {
      font-size: 19px;
    }
    .creature-hero {
      align-items: flex-start;
    }
    .creature-hero .eyebrow {
      font-size: 9px;
    }
    .live-note {
      font-size: 10px;
    }
  }
</style>
