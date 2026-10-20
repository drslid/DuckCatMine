<script lang="ts">
  import { onMount } from 'svelte';
  import { api, uploadPlugin, demo } from './api';
  import { modal } from './modal';
  import Icon from './Icon.svelte';
  let {
    toast,
    restart,
    scheduled = false,
  }: { toast: any; restart: () => void; scheduled?: boolean } = $props();
  let inventory = $state<any>({ installed: [], pending: [] }),
    installedQuery = $state(''),
    showAll = $state(false),
    query = $state(''),
    hits = $state<any[]>([]),
    catalogVersion = $state(''),
    pane = $state('installed'),
    loading = $state(true),
    busy = $state(false),
    error = $state(''),
    selection = $state<any>(null),
    version = $state(''),
    file = $state<File | null>(null),
    progress = $state(''),
    searching = $state(false),
    stopped = false,
    searchSerial = 0;
  let installedMatches = $derived(
    inventory.installed.filter((p) =>
      (p.name + ' ' + p.filename)
        .toLowerCase()
        .includes(installedQuery.toLowerCase()),
    ),
  );
  let installedVisible = $derived(
    installedQuery || showAll
      ? installedMatches
      : installedMatches.slice(0, 10),
  );
  async function refresh() {
    inventory = await api('/api/plugins');
  }
  onMount(() => {
    refresh()
      .catch((e) => (error = e.message))
      .finally(() => (loading = false));
    return () => {
      stopped = true;
      searchSerial++;
    };
  });
  async function search(e?: Event) {
    e?.preventDefault();
    const serial = ++searchSerial;
    searching = true;
    error = '';
    try {
      const result = await api(
        '/api/plugins/search?query=' + encodeURIComponent(query.trim()),
      );
      if (serial === searchSerial) {
        hits = result.hits;
        catalogVersion = result.gameVersion;
      }
    } catch (e) {
      if (serial === searchSerial) error = e.message;
    } finally {
      if (serial === searchSerial) searching = false;
    }
  }
  async function choose(project) {
    busy = true;
    try {
      selection = await api('/api/plugins/project/' + project.id);
      version =
        selection.versions.find((v) => v.type === 'release')?.id ||
        selection.versions[0]?.id ||
        '';
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function wait(id) {
    while (!stopped) {
      const result = await api('/api/jobs/' + id);
      if (result.status === 'done') {
        if (!result.ok) throw new Error(result.error || 'Import refusé.');
        return result.message;
      }
      await new Promise((resolve) => setTimeout(resolve, 1500));
    }
  }
  async function prepare() {
    busy = true;
    progress = 'Transfert et vérification du fichier…';
    error = '';
    try {
      const result = file
        ? await uploadPlugin(file)
        : await api('/api/plugins/install', 'POST', {
            projectId: selection.id,
            versionId: version,
          });
      progress = 'Lecture des métadonnées et des dépendances…';
      const message = await wait(result.id);
      if (stopped) return;
      selection = null;
      file = null;
      pane = 'installed';
      await refresh();
      toast(message || 'Extension préparée.');
    } catch (e) {
      error = e.message;
      toast(e.message, true);
    } finally {
      busy = false;
      progress = '';
    }
  }
  async function cancel(id) {
    busy = true;
    try {
      await wait(
        (await api('/api/plugins/cancel', 'POST', { artifact: id })).id,
      );
      await refresh();
      toast('Ajout annulé.');
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  function selectFile(event) {
    const picked = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!picked) return;
    if (!/\.jar$/i.test(picked.name) || picked.size > 50 * 1024 ** 2) {
      toast('Choisis un fichier .jar de 50 Mio maximum.', true);
      return;
    }
    file = picked;
    selection = null;
    error = '';
  }
</script>

<article class="card extensions-header">
  <div>
    <span class="eyebrow">À TA FAÇON</span>
    <h2>Les extensions</h2>
    <p class="muted">
      Ajoute des possibilités au jeu. Les changements prennent effet après une
      sauvegarde et une relance.
    </p>
  </div>
  <label class="secondary plugin-upload"
    ><Icon name="upload" size={17} />Importer un .jar<input
      type="file"
      accept=".jar"
      onchange={selectFile}
      disabled={busy || scheduled}
    /></label
  >
</article>
<div class="segments plugin-segments">
  <button
    class:active={pane === 'installed'}
    onclick={() => (pane = 'installed')}
    >Installées <span>{inventory.installed.length}</span></button
  ><button
    class:active={pane === 'catalog'}
    onclick={() => {
      pane = 'catalog';
      if (!catalogVersion) search();
    }}>Catalogue <Icon name="search" size={15} /></button
  >
</div>
{#if error}<p class="error" role="alert">{error}</p>{/if}
{#if inventory.pending.length}<article class="card plugin-pending">
    <div class="card-heading">
      <div>
        <h2>
          {inventory.pending.length} changement{inventory.pending.length > 1
            ? 's'
            : ''} en attente
        </h2>
        <p class="muted">La partie en cours continue normalement.</p>
      </div>
      <Icon name="refresh" />
    </div>
    {#each inventory.pending as p}<div class="plugin-pending-row">
        <div>
          <strong>{p.name}</strong><small
            >{p.previousVersion ? p.previousVersion + ' → ' : ''}{p.version} · {p.source}</small
          >{#if p.missingDependencies?.length}<p class="error">
              À ajouter : {p.missingDependencies.join(', ')}
            </p>{/if}
        </div>
        <button
          class="quiet"
          disabled={busy || scheduled}
          onclick={() => cancel(p.id)}>Annuler</button
        >
      </div>{/each}
    <button
      class="primary"
      onclick={restart}
      disabled={busy ||
        scheduled ||
        inventory.pending.some((p) => p.missingDependencies?.length)}
      ><Icon name="save" size={17} />{scheduled
        ? 'Relance déjà programmée'
        : 'Sauvegarder et programmer la relance'}</button
    ><small class="muted"
      >Attend 5 minutes sans joueur. Confirmation avec ton mot de passe.</small
    >
  </article>{/if}
{#if pane === 'installed'}<article class="card">
    <div class="card-heading">
      <h2>Dans le serveur</h2>
      <button
        class="quiet"
        onclick={() => refresh().catch((e) => toast(e.message, true))}
        ><Icon name="refresh" size={16} />Actualiser</button
      >
    </div>
    <p class="muted">
      Fichiers présents sur le serveur. La console permet de vérifier qu’ils se
      chargent correctement.
    </p>
    {#if loading}<p class="empty">
        Lecture des extensions…
      </p>{:else if !inventory.observedAt}<p class="empty">
        Inventaire en cours de détection. Actualise dans quelques secondes.
      </p>{:else}<label class="plugin-search-field"
        ><Icon name="search" size={17} /><input
          type="search"
          bind:value={installedQuery}
          placeholder="Filtrer les extensions installées…"
          aria-label="Filtrer les extensions installées"
        /></label
      >
      <div class="plugin-list">
        {#each installedVisible as p}<div class="plugin-installed">
            <span class="plugin-mark" class:core={p.core}
              ><Icon name={p.core ? 'shield' : 'plugin'} size={19} /></span
            >
            <div>
              <strong>{p.name}</strong><small
                >{p.version}{p.warning
                  ? ' · Métadonnées à vérifier'
                  : ''}</small
              >
            </div>
            {#if p.core}<span class="pill">Socle</span>{:else}<span
                class="plugin-file">{p.filename}</span
              >{/if}
          </div>{/each}
      </div>
      {#if !installedQuery && !showAll && installedMatches.length > 10}<button
          class="quiet"
          onclick={() => (showAll = true)}
          >Voir les {installedMatches.length - 10} autres extensions</button
        >{/if}
      {#if !installedMatches.length}<p class="empty">
          Aucune extension correspondante.
        </p>{/if}
    {/if}
    <details class="disclosure">
      <summary>Quelles extensions choisir ?</summary>
      <p class="muted">
        Le catalogue filtre la version Minecraft et Paper/Bukkit. La
        compatibilité est déclarée par les auteurs ; vérifie leurs dépendances
        et teste les ajouts. Les extensions du socle sont mises à jour avec
        DuckCatMine.
      </p>
      <p class="muted">
        Un fichier .jar exécute du code sur ton serveur Minecraft. Importe
        uniquement des fichiers dont tu connais la source.
      </p>
    </details>
  </article>{:else}<article class="card">
    <form class="plugin-search" onsubmit={search}>
      <label class="plugin-search-field"
        ><Icon name="search" size={18} /><input
          type="search"
          bind:value={query}
          maxlength="80"
          placeholder="Permissions, quêtes, protection…"
          aria-label="Rechercher une extension"
        /></label
      ><button class="secondary" disabled={searching}>Rechercher</button>
    </form>
    <p class="muted">
      {demo
        ? 'Catalogue de démonstration'
        : 'Catalogue Modrinth'}{catalogVersion
        ? ' · Paper ' + catalogVersion
        : ''} · Compatibilité déclarée par les auteurs
    </p>
    {#if searching}<p class="empty" role="status">
        Recherche en cours…
      </p>{:else}<div class="plugin-catalog">
        {#each hits as p}<button
            class="plugin-result"
            onclick={() => choose(p)}
            disabled={busy || scheduled}
            ><span class="plugin-logo"
              >{#if p.icon}<img
                  src={p.icon}
                  alt=""
                  loading="lazy"
                  onerror={(e) =>
                    ((e.currentTarget as HTMLImageElement).style.visibility =
                      'hidden')}
                />{:else}<Icon name="plugin" size={26} />{/if}</span
            ><span
              ><strong>{p.name}</strong><small>{p.description}</small><span
                class="plugin-author"
                >{p.author} · {Number(p.downloads).toLocaleString('fr')} téléchargements</span
              ></span
            ><Icon name="chevron" size={17} /></button
          >{/each}
      </div>
      {#if !hits.length}<div class="empty">
          Aucune extension déclarée compatible. Essaie un autre nom ou importe
          un fichier .jar.
        </div>{/if}{/if}
  </article>{/if}
{#if selection || file}<div class="modal-backdrop" role="presentation">
    <div
      class="modal plugin-review"
      role="dialog"
      aria-modal="true"
      aria-label="Préparer une extension"
      tabindex="-1"
      use:modal={{
        close: () => {
          if (!busy) {
            selection = null;
            file = null;
          }
        },
      }}
    >
      <div class="modal-heading">
        <h2>{file ? 'Importer une extension' : selection.name}</h2>
        <button
          class="icon-button"
          aria-label="Fermer"
          disabled={busy}
          onclick={() => {
            selection = null;
            file = null;
          }}><Icon name="close" /></button
        >
      </div>
      {#if file}<div class="plugin-file-review">
          <Icon name="plugin" size={30} /><strong>{file.name}</strong><span
            >{(file.size / 1024 ** 2).toLocaleString('fr', {
              maximumFractionDigits: 1,
            })} Mio</span
          >
        </div>
        <p class="muted">
          Le nom, la version et les dépendances seront lus dans le fichier, sans
          l’exécuter.
        </p>{:else}<p class="muted">{selection.description}</p>
        <label
          >Version pour Minecraft {selection.gameVersion}<select
            bind:value={version}
            disabled={busy}
            >{#each selection.versions as v}<option value={v.id}
                >{v.name} · {v.type === 'release' ? 'stable' : v.type} · {new Date(
                  v.date,
                ).toLocaleDateString('fr')}</option
              >{/each}</select
          ></label
        >{#if selection.versions.find((v) => v.id === version)?.dependencies.length}<p
            class="muted"
          >
            Dépendances requises : {selection.versions
              .find((v) => v.id === version)
              .dependencies.join(', ')}. Elles doivent aussi être ajoutées.
          </p>{/if}<a
          class="quiet"
          href={selection.url}
          target="_blank"
          rel="noopener noreferrer"
          >Voir la fiche et les prérequis sur Modrinth <Icon
            name="arrow"
            size={15}
          /></a
        >{/if}
      <p class="plugin-review-note">
        <Icon name="save" size={19} /><span
          >Préparation maintenant, activation après une relance sauvegardée.
          Aucun changement pendant la partie.</span
        >
      </p>
      {#if error}<p class="error" role="alert">{error}</p>{/if}{#if progress}<p
          role="status"
        >
          {progress}
        </p>{/if}<button
        class="primary full"
        disabled={busy || scheduled || (!file && !version)}
        onclick={prepare}
        >{busy ? 'Vérification…' : 'Préparer cet ajout'}</button
      >
    </div>
  </div>{/if}
