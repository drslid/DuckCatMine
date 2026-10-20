<script lang="ts">
  import { onMount } from 'svelte';
  import { api, fold } from './api';
  import SkinFigure from './SkinFigure.svelte';
  import Icon from './Icon.svelte';
  let { player, toast } = $props();
  let query = $state(''),
    skins = $state<any[]>([]),
    selected = $state<any>(null),
    loading = $state(false),
    busy = $state(false),
    error = $state(''),
    source = $state(''),
    account = $state('');
  let previewReady = $state(false);
  let timer,
    sequence = 0;
  const translations = {
    chevalier: 'knight',
    mage: 'wizard',
    sorcier: 'wizard',
    chat: 'cat',
    fantastique: 'fantasy',
  };
  async function search(value = query) {
    query = value;
    const id = ++sequence;
    const q = translations[fold(value)] || fold(value).trim();
    if (q && q.length < 2) return;
    loading = true;
    error = '';
    try {
      const data = await api(
        `/api/skins/catalog?query=${encodeURIComponent(q)}`,
      );
      if (id === sequence) {
        skins = data.skins;
        source = data.source;
        selected = null;
      }
    } catch (e) {
      if (id === sequence) error = e.message;
    } finally {
      if (id === sequence) loading = false;
    }
  }
  async function apply() {
    busy = true;
    try {
      await api(
        selected.mode === 'player'
          ? '/api/skins/apply'
          : '/api/skins/apply-gallery',
        'POST',
        selected.mode === 'player'
          ? { player, skin: selected.name }
          : { player, id: selected.id, source: selected.source || 'craftdex' },
      );
      toast(`Skin appliqué à ${player}.`);
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  onMount(() => {
    search();
    return () => clearTimeout(timer);
  });
</script>

<div class="catalog-search">
  <Icon name="search" /><input
    type="search"
    aria-label="Chercher un skin"
    placeholder="Chevalier, mage, robot…"
    bind:value={query}
    oninput={() => {
      clearTimeout(timer);
      timer = setTimeout(() => search(), 450);
    }}
  />
</div>
<div class="segments wrap">
  {#each [['', 'Tendances'], ['knight', 'Chevalier'], ['wizard', 'Mage'], ['robot', 'Robot'], ['cat', 'Chat']] as [id, label]}<button
      class:active={query === id}
      onclick={() => search(id)}>{label}</button
    >{/each}
</div>
{#if loading}<p class="muted" role="status">Recherche des skins…</p>{/if}
{#if error}<p class="error" role="alert">{error}</p>
  <button class="secondary" onclick={() => search()}>Réessayer</button>{/if}
<div class="skin-layout">
  <div class="skin-grid">
    {#each skins as skin, i}<button
        class="skin-tile"
        class:selected={selected?.id === skin.id}
        onclick={() => (selected = skin)}
        ><SkinFigure
          src={`/api/skins/texture/${skin.hash}`}
          variant={i}
        /><strong>{skin.name || skin.creator || 'Personnage'}</strong></button
      >{/each}{#if !skins.length && !loading && !error}<p class="empty">
        Aucun résultat. Essaie un autre mot.
      </p>{/if}
  </div>
  <aside class="skin-preview">
    <div class="skin-figures">
      <SkinFigure
        src={selected?.mode === 'player'
          ? `/api/skins/player-image/${selected.name}`
          : selected?.hash
            ? `/api/skins/texture/${selected.hash}`
            : ''}
        variant={Math.max(0, skins.indexOf(selected))}
        onready={(value) => (previewReady = value)}
      /><SkinFigure
        src={selected?.mode === 'player'
          ? `/api/skins/player-image/${selected.name}`
          : selected?.hash
            ? `/api/skins/texture/${selected.hash}`
            : ''}
        back
        variant={Math.max(0, skins.indexOf(selected))}
      />
    </div>
    <strong>{selected?.name || selected?.creator || 'Choisis un skin'}</strong
    ><button
      class="primary full"
      disabled={!selected || !previewReady || busy}
      onclick={apply}>{busy ? 'Application…' : `Appliquer à ${player}`}</button
    >
  </aside>
</div>
<details class="disclosure">
  <summary>Utiliser le skin d’un compte Minecraft</summary>
  <form
    class="inline"
    onsubmit={(e) => {
      e.preventDefault();
      selected = { mode: 'player', name: account, id: `player:${account}` };
    }}
  >
    <input
      aria-label="Pseudo du skin"
      placeholder="Pseudo Minecraft"
      pattern={'[A-Za-z0-9_]{3,16}'}
      required
      bind:value={account}
    /><button class="secondary">Prévisualiser</button>
  </form>
  <a href="https://skinsrestorer.net/upload" target="_blank" rel="noreferrer"
    >Importer un PNG avec SkinsRestorer</a
  >
</details>
<small class="muted"
  >Source : <a
    href={source || 'https://craftdex.net'}
    target="_blank"
    rel="noreferrer">{query ? 'MineSkin' : 'Craftdex'}</a
  >. Le changement concerne ce serveur.</small
>
