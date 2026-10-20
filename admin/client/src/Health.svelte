<script lang="ts">
  import Icon from './Icon.svelte';
  import { compactMemory } from './health.js';
  let { health, open }: { health: any; open: () => void } = $props();
</script>

<button
  class="health-strip"
  data-health={health.level}
  onclick={open}
  title={health.detail}
  aria-label={`Santé du serveur : ${health.label}. Ouvrir le diagnostic`}
>
  <span class="health-state"><i></i><strong>{health.label}</strong></span>
  <span class="health-value"
    ><Icon name="cpu" size={15} /><span
      >CPU <b>{health.cpu == null ? '—' : Math.round(health.cpu) + ' %'}</b
      ></span
    ></span
  >
  <span class="health-value"
    ><Icon name="memory" size={15} /><span
      >RAM <b
        >{compactMemory(health.used)}{health.limit
          ? ' / ' + compactMemory(health.limit)
          : ''} Gio</b
      ></span
    ></span
  >
  <Icon name="chevron" size={14} />
</button>
