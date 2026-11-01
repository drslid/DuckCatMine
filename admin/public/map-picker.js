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
    style.textContent = '.bm-marker-player{pointer-events:auto!important;cursor:pointer;touch-action:manipulation}'
      + '.dc-infected-dot{display:block;width:8px;height:8px;background:#f57f73;border:1.5px solid #17241c;border-radius:50%;box-shadow:0 0 0 1px #f57f7355;pointer-events:auto}'
      + '.dc-infected-elite{background:#f4b65f;width:10px;height:10px}.dc-infected-boss{background:#c49af8;width:12px;height:12px;border-radius:3px}';
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
  let infectedLayer = null;
  const infectedMarkers = new Map();
  const escapeLabel = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const redraw = () => { if (typeof window.bluemap.mapViewer.redraw === 'function') window.bluemap.mapViewer.redraw(); };
  const infectedVisibility = () => {
    if (infectedLayer) infectedLayer.visible = infectedMarkers.size > 0 && window.bluemap.mapViewer.map?.data?.id === scenarioMap;
  };
  const updateInfected = data => {
    if (!scenarioMap || data.world !== scenarioMap) return;
    if (data.visible !== true) {
      const changed = infectedMarkers.size > 0;
      for (const {marker} of infectedMarkers.values()) infectedLayer.remove(marker);
      infectedMarkers.clear(); infectedVisibility();
      if (changed) redraw();
      return;
    }
    const points = Array.isArray(data.points) ? data.points.slice(0,512) : [];
    const active = new Set();
    let changed = false;
    for (const p of points) {
      if (!p || typeof p.id !== 'string' || !/^[a-f0-9-]{36}$/i.test(p.id) || typeof p.type !== 'string'
          || !/^[a-z][a-z0-9_-]{0,47}$/.test(p.type) || ![p.x,p.y,p.z].every(n => typeof n === 'number' && Number.isFinite(n) && Math.abs(n) <= 30000000)) continue;
      active.add(p.id);
      const label = typeof data.labels?.[p.type] === 'string' ? data.labels[p.type].slice(0,80) : p.type;
      let entry = infectedMarkers.get(p.id);
      if (!entry || entry.label !== label || entry.type !== p.type) {
        if (!infectedLayer) {
          infectedLayer = new window.BlueMap.MarkerSet('duckcatmine-infected');
          infectedLayer.data.toggleable = false;
          window.bluemap.mapViewer.markers.add(infectedLayer);
        }
        const marker = entry?.marker || new window.BlueMap.HtmlMarker('dc-infected-' + p.id);
        const kind = p.type === 'abomination' ? 'dc-infected-boss' : ['howler','breaker','runner','pursuer'].includes(p.type) ? 'dc-infected-elite' : '';
        marker.updateFromData({position:{x:p.x,y:p.y+1.5,z:p.z},label,listed:false,sorting:-20,anchor:{x:.5,y:.5},classes:[],
          html:`<span class="dc-infected-dot ${kind}" role="img" aria-label="${escapeLabel(label)}" title="${escapeLabel(label)}"></span>`});
        if (!entry) infectedLayer.add(marker);
        infectedMarkers.set(p.id,{marker,label,type:p.type});
        changed = true;
      } else if (entry.marker.position.x !== p.x || entry.marker.position.y !== p.y+1.5 || entry.marker.position.z !== p.z) {
        entry.marker.position.set(p.x,p.y+1.5,p.z);
        changed = true;
      }
    }
    for (const [id,entry] of infectedMarkers) if (!active.has(id)) {
      infectedLayer.remove(entry.marker); infectedMarkers.delete(id); changed = true;
    }
    infectedVisibility();
    if (changed) redraw();
  };
  window.addEventListener('message', (event) => {
    if (event.source !== window.parent || event.origin !== parentOrigin || event.data?.source !== 'duckcatmine-map-picker') return;
    const data = event.data;
    if (data.type === 'infected') updateInfected(data);
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
    infectedVisibility();
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
