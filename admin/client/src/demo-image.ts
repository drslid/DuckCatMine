export function image(id: string) {
  const name = id.replace('minecraft:', '');
  const egg = name.endsWith('_egg');
  const green = name.includes('emerald');
  const stone = name.includes('stone') || name.includes('iron');
  const color = green
    ? '#6bc58d'
    : stone
      ? '#a9b9be'
      : name.includes('diamond')
        ? '#7fd6db'
        : name.includes('apple')
          ? '#dbaa64'
          : '#bd9b65';
  const art = egg
    ? `<path d="M10 3h12l7 12v11l-6 6H9l-6-6V15z" fill="${color}"/><path d="M10 9h6v6h-6zM20 19h6v6h-6z" fill="#758a62"/>`
    : name.includes('diamond') || green
      ? `<path d="M8 3h18l7 10-16 20L1 13z" fill="${color}"/><path d="M8 3 1 13h32M8 3l9 30 9-30" fill="none" stroke="#e6f7ec" stroke-width="2"/>`
      : `<path d="m2 10 15-8 15 8v17l-15 8-15-8z" fill="${color}"/><path d="m2 10 15 8 15-8M17 18v17" fill="none" stroke="#493e34" stroke-width="2"/>`;
  return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 34 36">${art}</svg>`)}`;
}
