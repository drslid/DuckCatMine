<script lang="ts">
  import { onMount } from 'svelte';

  import { api, command, demo, fold, iconUrl } from './api';

  import { getCatalog } from './catalog';

  import Icon from './Icon.svelte';

  import BuildPreview from './BuildPreview.svelte';

  let {
    players = [],
    toast,
    start = '',
    policy = 'live',
  }: {
    players?: string[];
    toast: any;
    start?: string;
    policy?: string;
  } = $props();

  let mode = $state('explore'),
    frame = $state<HTMLIFrameElement>(),
    ready = $state(demo),
    point = $state<any>(null),
    models = $state<any[]>([]),
    model = $state<any>(null),
    mobs = $state<any[]>([]),
    creature = $state(''),
    count = $state(1),
    search = $state(''),
    busy = $state(false),
    terrain = $state(''),
    checked = $state(false),
    confirmPlacement = $state(false),
    chosenPlayer = $state(''),
    mapError = $state('');

  let toolsOpen = $state(false);

  let sequence = 0,
    checkTimer,
    readyTimer;

  let choices = $derived(
    mobs
      .filter((m) => fold(`${m.id} ${m.name}`).includes(fold(search)))
      .slice(0, 24),
  );

  async function switchMode(value) {
    mode = value;
    confirmPlacement = false;
    toolsOpen = value !== 'explore';
    sequence++;
    checked = false;

    if (value === 'build' && !models.length)
      try {
        const result = await api('/api/towers/catalog');
        models = result.towers;
        if (!result.active)
          toast('Le plugin de constructions n’est pas chargé.', true);
      } catch (e) {
        toast(e.message, true);
      }

    if (value === 'mobs' && !mobs.length)
      try {
        const data = await getCatalog();
        mobs = data.items
          .filter((id) => id.endsWith('_spawn_egg'))
          .map((id) => ({
            id: id.replace('minecraft:', '').replace('_spawn_egg', ''),
            name: (data.meta[id]?.name || id).replace(
              /^Œuf d’apparition (?:de |d’)?/i,
              '',
            ),
            icon: id,
          }));
      } catch (e) {
        toast(e.message, true);
      }
  }

  async function check() {
    const id = ++sequence;
    checked = false;
    terrain = '';

    if (!model || !point || mode !== 'build') return;

    terrain = 'Vérification du terrain…';

    try {
      const result = await api('/api/towers/check', 'POST', {
        id: model.id,
        ...point,
      });
      if (id === sequence) {
        terrain = result.result;
        checked = true;
      }
    } catch (e) {
      if (id === sequence) terrain = e.message;
    }
  }

  function choosePoint(value) {
    point = value;
    confirmPlacement = false;
    clearTimeout(checkTimer);
    checkTimer = setTimeout(check, 200);
  }

  async function place() {
    busy = true;

    try {
      const result = await api(
        mode === 'build' ? '/api/towers/place' : '/api/mobs/spawn',
        'POST',
        mode === 'build'
          ? { id: model.id, ...point }
          : { mob: creature, ...point, count },
      );
      toast(result.result || `${result.spawned} créature(s) apparue(s).`);
      confirmPlacement = false;
      toolsOpen = false;
      checked = false;
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }

  async function upload(event) {
    const file = event.currentTarget.files?.[0];

    if (!file) return;

    if (!/\.schem$/i.test(file.name) || file.size > 1048576) {
      toast('Choisis un fichier .schem de 1 Mio maximum.', true);
      return;
    }

    busy = true;

    try {
      const bytes = new Uint8Array(await file.arrayBuffer());
      let raw = '';

      for (let i = 0; i < bytes.length; i += 8192)
        raw += String.fromCharCode(...bytes.subarray(i, i + 8192));

      const result = await api('/api/towers/upload', 'POST', {
        filename: file.name,
        data: btoa(raw),
      });

      models = [...models, result.tower];
      model = result.tower;
      toast('Construction importée. Choisis son emplacement.');
      check();
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }

  async function center() {
    if (!chosenPlayer) {
      toast('Choisis un joueur à suivre.');
      return;
    }

    try {
      const { status: s } = await api(
        `/api/players/${chosenPlayer}/details?section=none`,
      );
      if (frame?.contentWindow)
        frame.contentWindow.location.hash = `#${s.world}:${s.x}:${s.y}:${s.z}:280:0:0:0:0:flat`;
    } catch (e) {
      toast(e.message, true);
    }
  }

  onMount(() => {
    const receive = (event) => {
      if (
        event.origin !== location.origin ||
        event.source !== frame?.contentWindow ||
        event.data?.source !== 'duckcatmine-map-picker'
      )
        return;

      if (event.data.type === 'ready') {
        ready = true;
        clearTimeout(readyTimer);
        mapError = '';
      }

      if (event.data.type === 'point') {
        const { world, x, y, z } = event.data;
        if (
          ['world', 'world_the_nether', 'world_the_end'].includes(world) &&
          [x, y, z].every(Number.isInteger)
        )
          choosePoint({ world, x, z });
      }

      if (event.data.type === 'clear') {
        point = null;
        checked = false;
        confirmPlacement = false;
      }
    };

    window.addEventListener('message', receive);

    readyTimer = setTimeout(() => {
      if (!ready)
        mapError =
          'La carte met du temps à répondre. Vérifie que BlueMap est démarré.';
    }, 20000);

    return () => {
      window.removeEventListener('message', receive);
      clearTimeout(checkTimer);
      clearTimeout(readyTimer);
    };
  });
</script>

<div class="section-heading">
  <div>
    <p class="eyebrow">LE MONDE, EN DIRECT</p>
    <h1>Une carte. Toutes les possibilités.</h1>
  </div>
  <div class="segments">
    {#each [['explore', 'map', 'Explorer'], ['build', 'build', 'Construire'], ['mobs', 'paw', 'Créatures']] as [id, icon, label]}<button
        class:active={mode === id}
        onclick={() => switchMode(id)}
        ><Icon name={icon} /><span>{label}</span></button
      >{/each}
  </div>
</div>

<div class="world-workspace" class:with-tools={mode !== 'explore'}>
  <div class="world-map-panel">
    <div class="map-toolbar">
      <span class="status-dot"></span><strong>Carte du serveur</strong>
      <div class="spacer"></div>
      <select aria-label="Joueur à voir sur la carte" bind:value={chosenPlayer}
        ><option value="">Voir un joueur…</option>{#each players as p}<option
            >{p}</option
          >{/each}</select
      ><button
        class="icon-button"
        title="Centrer sur le joueur"
        onclick={center}><Icon name="pin" /></button
      >
    </div>

    <div class="world-map">
      {#if demo}<svg
          class="demo-map"
          viewBox="0 0 800 600"
          role="img"
          aria-label="Carte de démonstration, cliquer pour choisir un point"
          ><defs
            ><pattern
              id="tiles"
              width="40"
              height="40"
              patternUnits="userSpaceOnUse"
              ><rect width="40" height="40" fill="#294c3f" /><path
                d="M0 0H40V40"
                fill="none"
                stroke="#325745"
              /></pattern
            ></defs
          ><rect width="800" height="600" fill="url(#tiles)" /><path
            d="M0 80Q300 90 280 300T800 500"
            fill="none"
            stroke="#3a777e"
            stroke-width="70"
          /><path
            d="M60 600 380 260 700 120"
            stroke="#9a9474"
            stroke-width="24"
            fill="none"
          />{#each [[180, 190], [260, 90], [510, 310], [600, 230], [620, 460], [120, 450]] as [x, y]}<path
              d={`M${x} ${y}h55v45h-55z`}
              fill="#7d8b6b"
            /><path d={`m${x - 7} ${y} 34-24 35 24z`} fill="#b1956c" />{/each}<g
            transform="translate(380 260)"
            ><rect x="-35" y="-35" width="70" height="70" fill="#c9b68a" /><rect
              x="-23"
              y="-23"
              width="46"
              height="46"
              fill="#7e9472"
            /><text y="70" text-anchor="middle" fill="#e8eee4" font-size="16"
              >Village du spawn</text
            ></g
          >{#each [[440, 220, 'Alex'], [250, 380, 'Sam']] as [x, y, name]}<g
              transform={`translate(${x} ${y})`}
              ><circle
                r="12"
                fill="#c8ee8c"
                stroke="#162b23"
                stroke-width="4"
              /><text y="-24" text-anchor="middle" fill="white" font-size="16"
                >{name}</text
              ></g
            >{/each}{#if point}<g
              transform={`translate(${point.x + 400} ${point.z + 300})`}
              ><circle r="12" fill="#efbd83" /><circle
                r="22"
                fill="none"
                stroke="#efbd83"
                stroke-width="3"
              /></g
            >{/if}<rect
            width="800"
            height="600"
            fill="transparent"
            role="button"
            tabindex="0"
            aria-label="Choisir un emplacement sur la carte"
            onclick={(e) => {
              const r = e.currentTarget.getBoundingClientRect();
              choosePoint({
                world: 'world',
                x: Math.round(((e.clientX - r.left) / r.width) * 800 - 400),
                z: Math.round(((e.clientY - r.top) / r.height) * 600 - 300),
              });
            }}
            onkeydown={(e) => {
              if (e.key === 'Enter')
                choosePoint({ world: 'world', x: 0, z: 0 });
            }}
          /></svg
        >
      {:else}<iframe
          bind:this={frame}
          src={`/map-picker-view/${start}`}
          title="Carte unique du serveur et des joueurs en temps réel"
          referrerpolicy="same-origin"
        ></iframe>{#if !ready}<div class="map-status" role="status">
            <Icon name="map" size={30} />
            <p>{mapError || 'Ouverture de la carte…'}</p>
          </div>{/if}{/if}
    </div>
    <div class="mobile-world-actions">
      {#if mode !== 'explore'}<button
          class="secondary"
          onclick={() => {
            confirmPlacement = false;
            toolsOpen = true;
          }}
          >{mode === 'build'
            ? model?.name || 'Choisir un modèle'
            : creature || 'Choisir une créature'}</button
        ><button
          class="primary"
          disabled={!ready ||
            !point ||
            busy ||
            (mode === 'build' ? !checked : !creature)}
          onclick={() => {
            confirmPlacement = true;
            toolsOpen = true;
          }}>Poser ici</button
        >{/if}
    </div>
    <div class="map-caption">
      <Icon name="pin" />{point
        ? `Point choisi · X ${point.x} · Z ${point.z}`
        : mode === 'explore'
          ? policy === 'idle'
            ? 'Joueurs en direct. Terrain actualisé hors partie.'
            : 'Les positions des joueurs sont affichées sur cette carte.'
          : 'Touche la carte pour choisir un emplacement.'}
    </div>
  </div>

  {#if mode !== 'explore'}<button
      class="tool-backdrop"
      class:open={toolsOpen}
      aria-label="Fermer le choix sur la carte"
      onclick={() => (toolsOpen = false)}
    ></button>
    <aside
      class="world-tools"
      class:tools-open={toolsOpen}
      class:confirming={confirmPlacement}
    >
      <button
        class="icon-button world-tools-close"
        aria-label="Revenir à la carte"
        onclick={() => (toolsOpen = false)}><Icon name="close" /></button
      >
      <h2>
        {confirmPlacement
          ? 'Confirmer la pose'
          : mode === 'build'
            ? 'Choisir une construction'
            : 'Choisir une créature'}
      </h2>

      {#if mode === 'build'}<div class="build-grid">
          {#each models as tower}<button
              class="build-tile"
              class:selected={model?.id === tower.id}
              onclick={() => {
                model = tower;
                toolsOpen = false;
                check();
              }}
              ><BuildPreview {tower} /><strong>{tower.name}</strong><small
                >{tower.width} × {tower.height} × {tower.length}{tower.defensive
                  ? ` · portée ${tower.range}`
                  : ''}</small
              ></button
            >{/each}
        </div>
        <label class="upload-button secondary"
          ><Icon name="build" />Importer un .schem<input
            type="file"
            accept=".schem"
            onchange={upload}
            disabled={busy}
          /></label
        ><small class="muted"
          >1 Mio maximum. L’aperçu du fichier est généré avant la pose.</small
        >
      {:else}<div class="catalog-search">
          <Icon name="search" /><input
            type="search"
            bind:value={search}
            placeholder="Cheval, villageois, zombie…"
            aria-label="Rechercher une créature"
          />
        </div>
        <div class="creature-grid">
          {#each choices as m}<button
              class="item-tile"
              class:selected={creature === m.id}
              onclick={() => {
                creature = m.id;
                toolsOpen = false;
              }}
              ><img src={iconUrl(m.icon)} alt="" loading="lazy" /><span
                >{m.name}</span
              ></button
            >{/each}
        </div>
        <label
          >Nombre de créatures<input
            type="number"
            min="1"
            max="8"
            bind:value={count}
          /></label
        >{/if}

      <div class="placement">
        <small>EMPLACEMENT</small><strong
          >{point
            ? `X ${point.x} · Z ${point.z}`
            : 'Choisis un point sur la carte'}</strong
        >{#if terrain}<p class:success={checked}>
            {terrain.replace(/^DCTOWER:/, '')}
          </p>{/if}{#if confirmPlacement}<p>
            Confirmer {mode === 'build'
              ? `la pose de ${model.name}`
              : `l’apparition de ${count} × ${creature}`} ici ?
          </p>
          <div class="inline">
            <button class="primary" disabled={busy} onclick={place}
              >Confirmer</button
            ><button class="quiet" onclick={() => (confirmPlacement = false)}
              >Annuler</button
            >
          </div>{:else}<button
            class="primary full"
            disabled={!ready ||
              !point ||
              busy ||
              (mode === 'build' ? !checked : !creature)}
            onclick={() => (confirmPlacement = true)}
            >{mode === 'build'
              ? 'Construire ici'
              : 'Faire apparaître ici'}</button
          >{/if}
      </div>
    </aside>{/if}
</div>

<details class="disclosure">
  <summary>Temps, météo et règles du monde</summary>
  <div class="inline wrap">
    {#each [['time set day', 'sun', 'Jour'], ['time set night', 'moon', 'Nuit'], ['weather clear', 'sun', 'Ciel clair'], ['weather rain', 'cloud', 'Pluie']] as [cmd, icon, label]}<button
        class="secondary"
        onclick={async () => {
          try {
            await command(cmd);
            toast(`${label} appliqué.`);
          } catch (e) {
            toast(e.message, true);
          }
        }}><Icon name={icon} />{label}</button
      >{/each}
  </div>
</details>
