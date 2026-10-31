(() => {
  if (window.parent === window || !window.bluemap?.events) return;
  const parentOrigin = window.location.origin;
  const requestedMap = new URLSearchParams(window.location.search).get('scenario');
  const scenarioMap = /^[a-z0-9_-]{1,48}$/i.test(requestedMap || '') ? requestedMap : null;
  const markerLabel = new URLSearchParams(window.location.search).has('construction') ? '▦ Construction à poser' : '📍 Point choisi';
  const report = (type, detail = {}) => window.parent.postMessage({ source: 'duckcatmine-map-picker', type, ...detail }, parentOrigin);
  if (scenarioMap && window.document?.head) {
    const style = window.document.createElement('style');
    // BlueMap's HTML renderer is a pointer-transparent overlay by default.
    style.textContent = '.bm-marker-player{pointer-events:auto!important;cursor:pointer;touch-action:manipulation}';
    window.document.head.appendChild(style);
  }
  // BlueMap versions can consume marker clicks before publishing a 3D interaction event.
  // Listen to their native player label as well, only in the dedicated scenario embed.
  if (scenarioMap) window.document?.addEventListener('click', (event) => {
    const marker = event.target?.closest?.('.bm-marker-player');
    const name = marker?.querySelector('.bm-player-name')?.textContent?.trim();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name || '')) return;
    event.preventDefault();
    event.stopPropagation();
    report('player', { name });
  }, true);
  const markers = new window.BlueMap.MarkerSet('duckcatmine-spawn-point');
  markers.data.toggleable = false;
  window.bluemap.mapViewer.markers.add(markers);
  let pin = null;
  const clear = () => {
    if (pin) markers.remove(pin);
    pin = null;
    report('clear');
  };
  window.addEventListener('message', (event) => {
    if (event.source !== window.parent || event.origin !== parentOrigin || event.data?.source !== 'duckcatmine-map-picker') return;
    const data = event.data;
    if (data.type === 'clear') clear();
    if (data.type === 'focus' && /^[a-z0-9_-]{1,48}$/i.test(data.world || '')
        && (!scenarioMap || data.world === scenarioMap)
        && [data.x, data.y, data.z].every(n => typeof n === 'number' && Number.isFinite(n) && Math.abs(n) <= 30000000)) {
      const current = window.location.hash.slice(1).split(':');
      const view = current.length === 10 && current.slice(5, 9).every(v => Number.isFinite(Number(v)))
        ? current.slice(5) : ['0', '0.5', '0', '0', 'perspective'];
      const distance = typeof data.distance === 'number' && Number.isFinite(data.distance) ? Math.min(5000, Math.max(20,data.distance)) : 350;
      window.location.hash = `#${data.world}:${data.x}:${data.y}:${data.z}:${distance}:${view.join(':')}`;
    }
  });
  const ready = () => {
    const world = window.bluemap.mapViewer.map?.data?.id;
    if (scenarioMap && world !== scenarioMap) {
      window.bluemap.switchMap(scenarioMap).catch(() => report('error'));
    } else report('ready', { world });
  };
  window.bluemap.events.addEventListener('bluemapMapChanged', () => { clear(); ready(); });
  window.bluemap.events.addEventListener('bluemapMapInteraction', (event) => {
    let object = event.detail?.object;
    while (object) {
      if (object.isPlayerMarker && /^[A-Za-z0-9_]{3,16}$/.test(object.data?.name || '')) {
        report('player', { name: object.data.name });
        return;
      }
      object = object.parent;
    }
    const hit = event.detail?.hiresHit || event.detail?.lowresHits?.find(Boolean);
    const point = hit?.pointOnLine || hit?.point;
    const world = window.bluemap.mapViewer.map?.data?.id;
    if (!point || !world || ![point.x, point.y, point.z].every(Number.isFinite)) return;
    const x = Math.floor(point.x), y = Math.floor(point.y), z = Math.floor(point.z);
    if (!pin) {
      pin = new window.BlueMap.HtmlMarker('duckcatmine-selected-point');
      markers.add(pin);
    }
    pin.updateFromData({ position: { x: x + 0.5, y: y + 1, z: z + 0.5 },
      label: markerLabel, listed: false, anchor: { x: 0.5, y: 1 }, classes: [],
      html: `<span style="display:block;pointer-events:none;border:2px solid #17251c;border-radius:999px;background:#b8f274;color:#17251c;padding:7px 11px;font:800 12px system-ui;white-space:nowrap;box-shadow:0 7px 22px #000a">${markerLabel}</span>` });
    report('point', { world, x, y, z });
  });
  ready();
})();
