<script lang="ts">
  let { status, player }: { status: any; player: any } = $props();
  let zoom = $state(100);
  let maximum = $derived(status.capabilities?.includes('radar-100') ? 100 : 10);
  let range = $derived(Math.min(zoom, maximum));
  let dots = $derived.by(() => {
    if (!player || status.stale) return [];
    const yaw = ((player.yaw || 0) * Math.PI) / 180;
    const project = (p: any, enemy: boolean, label: string) => {
      const dx = p.x - player.x,
        dz = p.z - player.z,
        d = Math.hypot(dx, dz);
      const far = d > range,
        scale = far ? 89 / (d || 1) : 70 / range;
      return {
        x: 100 + (-dx * Math.cos(yaw) - dz * Math.sin(yaw)) * scale,
        y: 100 - (-dx * Math.sin(yaw) + dz * Math.cos(yaw)) * scale,
        enemy: enemy && !far,
        far,
        label: `${label} · ${Math.round(d)} blocs`,
      };
    };
    return [
      ...(status.infectedPositions || [])
        .filter(
          (p: any) =>
            Math.hypot(
              p.x - player.x,
              p.z - player.z,
            ) <= range,
        )
        .map((p: any) =>
          project(p, true, status.archetypeLabels?.[p.type] || 'Infecté'),
        ),
      ...(status.survivors || [])
        .filter((p: any) => p.name !== player.name)
        .map((p: any) => project(p, p.infectionSeconds != null, p.name)),
    ];
  });
</script>

<div class="sonar" aria-label={`Sonar autour de ${player.name}`}>
  <div class="heading">
    <strong>Sonar · {range} blocs</strong><small>Devant ↑ · positions 5 s</small>
  </div>
  {#if maximum === 100}<div class="zoom" aria-label="Portée du sonar">{#each [50,100] as value}<button type="button" aria-pressed={zoom===value} onclick={()=>zoom=value}>{value} blocs</button>{/each}</div>{/if}
  <svg
    viewBox="0 0 200 200"
    role="img"
    aria-label="Survivants bleus, infectés rouges, joueurs éloignés hors du cercle"
  >
    <circle cx="100" cy="100" r="73" fill="#102b30" stroke="#438e83" />
    <circle cx="100" cy="100" r="36" fill="none" stroke="#29515b" />
    <circle cx="100" cy="100" r="18" fill="none" stroke="#29515b" />
    <path d="M27 100H173M100 27V173" stroke="#29515b" />
    {#each dots as dot}<circle
        cx={dot.x}
        cy={dot.y}
        r={dot.far ? 3.5 : 4.5}
        fill={dot.enemy ? '#ff625f' : '#58b7ff'}
        stroke="#0d2028"
        stroke-width="1.5"><title>{dot.label}</title></circle
      >{/each}
    <path d="M100 93l5 11-5-2-5 2z" fill="#f1f5ed" />
    <text x="100" y="20" text-anchor="middle">DEVANT</text>
  </svg>
  <p><span class="blue"></span>Survivants <span class="red"></span>Infectés</p>
  <small>Hors cercle : direction des joueurs éloignés.</small>
</div>

<style>
  .sonar {
    margin: 16px 0;
    padding: 12px;
    border: 1px solid var(--border);
    border-radius: 12px;
    background: #102127;
  }
  .heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    font-size: 12px;
  }
  .zoom { display:flex; gap:4px; margin-top:8px; }
  .zoom button { font:inherit; font-size:11px; padding:4px 8px; min-height:32px; color:var(--muted); background:transparent; border:1px solid var(--border); border-radius:6px; }
  .zoom button[aria-pressed='true'] { color:#b8efdd; border-color:#438e83; background:#183a39; }
  small {
    color: var(--muted);
    font-size: 10px;
  }
  svg {
    width: min(100%, 220px);
    display: block;
    margin: 4px auto;
  }
  text {
    fill: #97b4b7;
    font-size: 7px;
    letter-spacing: 1.5px;
  }
  p {
    display: flex;
    gap: 7px;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    margin: 0 0 7px;
  }
  p span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }
  .blue {
    background: #58b7ff;
  }
  .red {
    background: #ff625f;
  }
</style>
