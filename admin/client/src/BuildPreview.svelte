<script lang="ts">
  let { tower }: { tower: any } = $props();
  const colors = {
    W: '#aa8058',
    G: '#9bd4dc',
    L: '#80a76e',
    M: '#daae76',
    S: '#98a8aa',
    B: '#b5a3cb',
  };
</script>

<svg
  viewBox="0 0 96 96"
  role="img"
  aria-label={`Aperçu ${tower.name}`}
  class="build-preview"
>
  {#if tower.preview}
    {#each tower.preview.split('/') as row, z}{#each [...row] as cell, x}{#if cell !== '.'}<rect
            x={x * 5 + 8}
            y={z * 5 + 8}
            width="5"
            height="5"
            fill={colors[cell] || colors.B}
          />{/if}{/each}{/each}
  {:else}
    <path d="m18 77 30 14 31-14-31-14z" fill="#152c26" />
    <path
      d="m28 27 20 9v46l-20-9z"
      fill={tower.id === 'fantasy'
        ? '#9b80bd'
        : tower.id === 'modern'
          ? '#bbc6c7'
          : '#839b91'}
    />
    <path
      d="m48 36 20-9v46l-20 9z"
      fill={tower.id === 'fantasy'
        ? '#6b5794'
        : tower.id === 'modern'
          ? '#7d999a'
          : '#59766c'}
    />
    <path
      d="m25 26 23-11 23 11-23 11z"
      fill={tower.id === 'fantasy'
        ? '#c6abe1'
        : tower.id === 'modern'
          ? '#d3a578'
          : '#c8b78b'}
    />
    <path
      d="M25 25v-9l7-3v9m10-2v-9l7-3v9m13 8v-9l8-3v9"
      fill="none"
      stroke={tower.id === 'fantasy'
        ? '#c6abe1'
        : tower.id === 'modern'
          ? '#d3a578'
          : '#c8b78b'}
      stroke-width="5"
    />
    <path d="m35 42 6 3v12l-6-3zM55 48l6-3v12l-6 3z" fill="#233c36" />
  {/if}
</svg>
