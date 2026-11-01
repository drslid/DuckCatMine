<script lang="ts">
  import { onMount } from 'svelte';
  import Icon from './Icon.svelte';
  let {
    status,
    selected = '',
    onSelect,
    onPoint = () => {},
    showInfected = true,
  }: {
    status: any;
    selected?: string;
    onSelect: (name: string) => void;
    onPoint?: (point: any) => void;
    showInfected?: boolean;
  } = $props();
  let frame = $state<HTMLIFrameElement>();
  let ready = $state(false),
    error = $state(''),
    retry = $state(0),
    point = $state<any>(null);
  let world = $derived(String(status.blueMap?.map || status.world || ''));
  let waitTimer: ReturnType<typeof setTimeout>;
  let valid = $derived(/^[a-z0-9_-]{1,48}$/i.test(world));
  let src = $derived(
    `/map-picker-view/?scenario=${encodeURIComponent(world)}&v=2#${world}:${status.refuge?.x || 0}:80:${status.refuge?.z || 0}:900:0:0.5:0:0:perspective`,
  );

  function send(type: string, detail: any = {}) {
    frame?.contentWindow?.postMessage(
      { source: 'duckcatmine-map-picker', type, world, ...detail },
      location.origin,
    );
  }
  function center() {
    const player = status.survivors?.find((p: any) => p.name === selected);
    send(
      'focus',
      player
        ? { x: player.x, y: player.y ?? 80, z: player.z, distance: 240 }
        : {
            x: status.refuge?.x || 0,
            y: 80,
            z: status.refuge?.z || 0,
            distance: 900,
          },
    );
  }
  $effect(() => {
    if (ready) {
      const visible =
        showInfected &&
        !status.stale &&
        !!status.capabilities?.includes('infected-map');
      send('infected', {
        visible,
        points: visible ? status.infectedPositions || [] : [],
        labels: visible ? status.archetypeLabels || {} : {},
      });
    }
  });
  // Status updates only change markers/details. They must never replace the iframe or reset its camera.
  function waitForMap() {
    clearTimeout(waitTimer);
    waitTimer = setTimeout(() => {
      if (!ready)
        error =
          'La carte met du temps à répondre. Tu peux réessayer ou utiliser la vue légère.';
    }, 20000);
  }
  onMount(() => {
    waitForMap();
    const receive = (event: MessageEvent) => {
      if (
        event.origin !== location.origin ||
        event.source !== frame?.contentWindow ||
        event.data?.source !== 'duckcatmine-map-picker'
      )
        return;
      const data = event.data;
      if (data.type === 'ready' && data.world === world) {
        ready = true;
        error = '';
        clearTimeout(waitTimer);
      }
      if (data.type === 'error')
        error = 'La carte Ground Zero n’est pas disponible pour le moment.';
      if (
        data.type === 'player' &&
        status.survivors?.some((p: any) => p.name === data.name)
      )
        onSelect(data.name);
      if (
        data.type === 'point' &&
        data.world === world &&
        [data.x, data.y, data.z].every(Number.isInteger)
      ) {
        point = data;
        onPoint(data);
      }
      if (data.type === 'clear') {
        point = null;
        onPoint(null);
      }
    };
    window.addEventListener('message', receive);
    return () => {
      clearTimeout(waitTimer);
      window.removeEventListener('message', receive);
    };
  });
</script>

<div class="map-toolbar">
  <span class="live-dot" class:ready></span><strong>Carte interactive</strong>
  <div class="spacer"></div>
  <button class="secondary" disabled={!ready} onclick={center}
    ><Icon name="pin" size={15} />{selected
      ? 'Voir ce joueur'
      : 'Refuge'}</button
  >
</div>
<div class="map-frame">
  {#if valid}{#key world + retry}<iframe
        bind:this={frame}
        {src}
        title="Carte 3D de Ground Zero et des joueurs en direct"
        allow="fullscreen"
        referrerpolicy="same-origin"
      ></iframe>{/key}{/if}
  {#if !ready || error}<div class="map-status" role="status">
      <Icon name="map" size={30} />
      <p>{error || 'Ouverture de Ground Zero…'}</p>
      {#if error}<button
          class="secondary"
          onclick={() => {
            ready = false;
            error = '';
            retry++;
            waitForMap();
          }}>Réessayer</button
        >{/if}
    </div>{/if}
</div>
<div class="map-caption">
  <span
    >{point
      ? `X ${point.x} · Y ${point.y} · Z ${point.z}`
      : 'Zoom, rotation et déplacement comme dans Monde.'}</span
  ><span>Touche le sol pour te téléporter, ou un joueur pour ses actions.</span>
</div>

<style>
  .map-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-bottom: 10px;
  }
  .map-toolbar strong {
    font-size: 12px;
  }
  .map-toolbar button {
    font-size: 11px;
    padding: 8px 10px;
  }
  .spacer {
    flex: 1;
  }
  .live-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--muted);
  }
  .live-dot.ready {
    background: var(--accent);
  }
  .map-frame {
    position: relative;
    height: 560px;
    min-height: 320px;
    border: 1px solid var(--border);
    border-radius: 12px;
    overflow: hidden;
    background: #17221b;
  }
  iframe {
    display: block;
    width: 100%;
    height: 100%;
    border: 0;
  }
  .map-status {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    padding: 20px;
    text-align: center;
    background: var(--surface);
    color: var(--muted);
    font-size: 13px;
  }
  .map-status p {
    max-width: 320px;
    line-height: 1.6;
  }
  .map-caption {
    display: flex;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 7px;
    margin-top: 10px;
    color: var(--muted);
    font-size: 10px;
    line-height: 1.5;
  }
  @media (max-width: 600px) {
    .map-frame {
      height: clamp(340px, 65svh, 520px);
    }
    .map-toolbar button {
      min-height: 40px;
    }
    .map-caption {
      font-size: 11px;
    }
  }
</style>
