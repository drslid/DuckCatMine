<script lang="ts">
  let { name, size = 20 }: { name: string; size?: number } = $props();
  const paths = {
    cpu: 'M7 7h10v10H7zM9 1v4M15 1v4M9 19v4M15 19v4M1 9h4M1 15h4M19 9h4M19 15h4M10 10h4v4h-4z',
    memory:
      'M3 6h18v12H3zM7 10h3v4H7zM14 10h3v4h-3zM6 18v3M10 18v3M14 18v3M18 18v3',
    plugin:
      'M9 3H4v6h2a3 3 0 1 1 0 6H4v6h6v-2a3 3 0 1 1 6 0v2h5v-6h-2a3 3 0 1 1 0-6h2V3h-6v2a3 3 0 1 1-6 0z',
    upload: 'M12 16V3m-5 5 5-5 5 5M3 16v5h18v-5',
    home: 'm3 10 9-7 9 7v10H15v-6H9v6H3z',
    users:
      'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
    map: 'm3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3zM9 3v15M15 6v15',
    chat: 'M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5',
    settings:
      'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2',
    sun: 'M12 3V1M12 23v-2M3 12H1M23 12h-2M4 4l2 2M18 18l2 2M4 20l2-2M18 6l2-2M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0',
    moon: 'M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8',
    box: 'm12 3 9 5v8l-9 5-9-5V8zM3 8l9 5 9-5M12 13v8M7.5 5.5l9 5',
    build: 'm14 3 7 7-3 3-3-3-9 11-3-3 11-9-3-3z',
    heart:
      'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8',
    search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
    close: 'm6 6 12 12M6 18 18 6',
    arrow: 'M5 12h14m-6-6 6 6-6 6',
    refresh:
      'M20 7v5h-5M4 17v-5h5M6.1 7a7 7 0 0 1 11.6-2L20 8M4 16l2.3 3A7 7 0 0 0 18 17',
    shield: 'm12 3 9 4v5c0 6-9 10-9 10S3 18 3 12V7zM8 12l3 3 5-5',
    save: 'M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12l4 4v12a2 2 0 0 1-2 2M7 3v7h10V3M7 21v-7h10v7',
    bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
    logout: 'M9 21H5V3h4M9 12h12m-4-4 4 4-4 4',
    cloud:
      'M20 16a4 4 0 0 0-1-7.9 7 7 0 0 0-13-2A5 5 0 0 0 5 16h15M8 20l1-2M14 22l1-4',
    pin: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
    paw: 'M8 12c-2 1-4 3-3 6 1 4 5 0 7 0s6 4 7 0c1-3-1-5-3-6-3-3-5-3-8 0M6 8V5M10 6V3M15 6V3M19 8V5',
    more: 'M5 12h.01M12 12h.01M19 12h.01',
    check: 'm5 12 4 4L19 6',
    copy: 'M9 9h12v12H9zM15 5V3H3v12h2',
    chevron: 'm9 5 7 7-7 7',
    terminal: 'm4 5 7 7-7 7M13 19h7',
    skull:
      'M12 3a8 8 0 0 0-8 8c0 2.8 1.3 4.6 3 5.6V20h10v-3.4c1.7-1 3-2.8 3-5.6a8 8 0 0 0-8-8M9 11.5h.01M15 11.5h.01M10 20v-2.5M14 20v-2.5M11 15h2',
  };
</script>

<svg
  width={size}
  height={size}
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="1.7"
  stroke-linecap="round"
  stroke-linejoin="round"
  aria-hidden="true"><path d={paths[name] || paths.box} /></svg
>
