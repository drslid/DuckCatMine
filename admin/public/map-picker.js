(() => {
  if (window.parent === window || !window.bluemap?.events) return;
  const parentOrigin = window.location.origin;
  const markerLabel = new URLSearchParams(window.location.search).has('construction') ? '▦ Construction à poser' : '📍 Point choisi';
  const report = (type, detail = {}) => window.parent.postMessage({ source: 'duckcatmine-map-picker', type, ...detail }, parentOrigin);
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
    if (event.source === window.parent && event.origin === parentOrigin
        && event.data?.source === 'duckcatmine-map-picker' && event.data.type === 'clear') clear();
  });
  window.bluemap.events.addEventListener('bluemapMapChanged', clear);
  window.bluemap.events.addEventListener('bluemapMapInteraction', (event) => {
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
  report('ready');
})();
