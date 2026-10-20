<script lang="ts">
  import { onMount } from 'svelte';
  import { api, fold, iconUrl } from './api';
  import { getCatalog } from './catalog';
  import Icon from './Icon.svelte';
  let {
    player = '',
    select = null,
    toast,
  }: { player?: string; select?: any; toast: any } = $props();
  let catalog: any = $state({ items: [], meta: {} }),
    query = $state(''),
    category = $state('featured'),
    selected = $state(''),
    count = $state(1),
    busy = $state(false),
    limit = $state(48),
    error = $state('');
  let customAvailable = $state(true);
  let custom = $state<any[]>([]),
    slug = $state(''),
    customName = $state(''),
    lore = $state('');
  const favorites = [
    'diamond',
    'elytra',
    'golden_apple',
    'netherite_sword',
    'oak_planks',
    'torch',
    'bread',
    'rail',
    'minecart',
  ];
  let results = $derived(
    catalog.items.filter((id) => {
      const m = catalog.meta[id] || {};
      return !query
        ? category === 'featured'
          ? favorites.includes(id.replace('minecraft:', ''))
          : category === 'all' || m.category === category
        : fold(`${id} ${m.name}`).includes(fold(query));
    }),
  );
  onMount(async () => {
    try {
      catalog = await getCatalog();
    } catch (e) {
      error = e.message;
    }
  });
  async function give(mode) {
    busy = true;
    try {
      await api('/api/items/action', 'POST', {
        player,
        item: selected,
        count,
        mode,
      });
      toast(
        mode === 'drop'
          ? 'Objet déposé devant le joueur.'
          : 'Objet ajouté à l’inventaire.',
      );
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function loadCustom() {
    try {
      const response = await api('/api/custom-items');
      custom = response.items; customAvailable = response.available !== false;
    } catch (e) {
      toast(e.message, true);
    }
  }
  async function createCustom(e) {
    e.preventDefault();
    busy = true;
    try {
      await api('/api/custom-items', 'POST', {
        slug,
        name: customName,
        lore,
        item: selected,
      });
      await loadCustom();
      toast('Objet personnalisé créé.');
      slug = '';
      customName = '';
      lore = '';
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function event(kind) {
    if (
      kind === 'horde' &&
      !confirm(`Faire apparaître quatre zombies près de ${player} ?`)
    )
      return;
    busy = true;
    try {
      const r = await api('/api/events', 'POST', {
        kind,
        player,
        item: selected,
        count: Math.min(count, 8),
      });
      toast(r.result);
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
</script>

<div class="catalog-search">
  <Icon name="search" /><input
    aria-label="Rechercher un objet"
    type="search"
    bind:value={query}
    oninput={() => (limit = 48)}
    placeholder="Diamant, épée, blocs…"
  />
</div>
<div class="segments wrap" aria-label="Catégorie d’objets">
  {#each [['featured', 'Essentiels'], ['all', 'Tous'], ['blocks', 'Blocs'], ['tools', 'Outils'], ['food', 'Nourriture'], ['transport', 'Transport'], ['decor', 'Déco']] as [id, label]}<button
      class:active={category === id}
      onclick={() => {
        category = id;
        limit = 48;
      }}>{label}</button
    >{/each}
</div>
{#if error}<p class="error">{error}</p>{/if}
<div class="item-grid">
  {#each results.slice(0, limit) as id}<button
      class="item-tile"
      class:selected={selected === id}
      title={catalog.meta[id]?.name || id}
      onclick={() => {
        selected = id;
        if (select) select(id);
      }}
      ><img
        src={iconUrl(id)}
        alt=""
        loading="lazy"
        onerror={(e) =>
          ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')}
      /><span
        >{catalog.meta[id]?.name ||
          id.replace('minecraft:', '').replaceAll('_', ' ')}</span
      ></button
    >{/each}
</div>
{#if !results.length && catalog.items.length}<p class="empty">
    Aucun objet trouvé.
  </p>{/if}
{#if results.length > limit}<button
    class="quiet full"
    onclick={() => (limit += 48)}
    >Afficher plus ({results.length - limit})</button
  >{/if}
{#if !select}<div class="selection-bar">
    <div>
      <small>OBJET CHOISI</small><strong
        >{selected
          ? catalog.meta[selected]?.name || selected
          : 'Sélectionne un objet'}</strong
      >
    </div>
    <label
      >Quantité<input
        type="number"
        min="1"
        max="64"
        bind:value={count}
      /></label
    ><button
      class="primary"
      disabled={!selected || !player || busy}
      onclick={() => give('give')}>Donner</button
    ><button
      class="secondary"
      disabled={!selected || !player || busy}
      onclick={() => give('drop')}>Déposer devant</button
    >
  </div>{/if}
{#if !select}<details class="disclosure">
    <summary>Animer la partie</summary>
    <div class="inline wrap">
      <button
        class="secondary"
        disabled={!selected || busy}
        onclick={() => event('loot')}>Pluie de l’objet choisi</button
      ><button class="secondary" disabled={busy} onclick={() => event('speed')}
        >Défi vitesse · 60 s</button
      ><button class="secondary" disabled={busy} onclick={() => event('horde')}
        >Mini invasion</button
      ><button class="quiet" disabled={busy} onclick={() => event('stop')}
        >Arrêter l’invasion</button
      >
    </div>
  </details>
  <details
    class="disclosure"
    ontoggle={(e) => {
      if (e.currentTarget.open) loadCustom();
    }}
  >
    <summary>Objets personnalisés · mCraft</summary>
    {#if !customAvailable}<p class="muted">Extension optionnelle mCraft requise. Consulte le guide des extensions avant de l’installer.</p>{:else}{#each custom as item}<div
        class="setting-row"
      >
        <span>{item.name}</span><button
          class="secondary"
          onclick={async () => {
            try {
              await api('/api/custom-items/give', 'POST', {
                id: item.id,
                player,
                count: 1,
              });
              toast('Objet donné.');
            } catch (e) {
              toast(e.message, true);
            }
          }}>Donner</button
        >
      </div>{/each}
    <form onsubmit={createCustom}>
      <p class="muted">
        Sélectionne l’objet de base dans le catalogue, puis personnalise son nom
        et sa description.
      </p>
      <label
        >Identifiant<input
          bind:value={slug}
          pattern={'[a-z][a-z0-9_]{2,31}'}
          required
          placeholder="mon_objet"
        /></label
      ><label
        >Nom<input
          bind:value={customName}
          minlength="3"
          maxlength="40"
          required
        /></label
      ><label>Description<input bind:value={lore} maxlength="100" /></label
      ><button class="secondary" disabled={!selected || busy}
        >Créer avec l’objet choisi</button
      >
    </form>
    {/if}
  </details>{/if}
