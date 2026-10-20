<script lang="ts">
  import { demo } from './api';
  let {
    src = '',
    back = false,
    label = 'Aperçu du skin',
    variant = 0,
    onready = (_ready: boolean) => {},
  }: {
    src?: string;
    back?: boolean;
    label?: string;
    variant?: number;
    onready?: (ready: boolean) => void;
  } = $props();
  let canvas: HTMLCanvasElement;
  $effect(() => {
    const source = src,
      reverse = back,
      color = variant,
      notify = onready;
    if (!canvas) return;
    const c = canvas.getContext('2d');
    c.clearRect(0, 0, 16, 32);
    c.imageSmoothingEnabled = false;
    if (demo) {
      const colors = ['#70b6bf', '#a993d9', '#e3ad67', '#c281a2', '#80aa74'];
      c.fillStyle = '#cfaa85';
      c.fillRect(4, 0, 8, 8);
      c.fillRect(0, 8, 4, 12);
      c.fillRect(12, 8, 4, 12);
      c.fillStyle = colors[color % colors.length];
      c.fillRect(4, 8, 8, 12);
      c.fillStyle = '#3b415d';
      c.fillRect(4, 20, 8, 12);
      c.fillStyle = '#514033';
      c.fillRect(4, 0, 8, 3);
      if (!reverse) {
        c.fillStyle = '#222d37';
        c.fillRect(5, 4, 2, 1);
        c.fillRect(9, 4, 2, 1);
      }
      notify(true);
      return;
    }
    if (!source) {
      notify(false);
      return;
    }
    notify(false);
    let alive = true;
    const img = new Image();
    img.onload = () => {
      if (!alive) return;
      const modern = img.height >= 64;
      const pieces = reverse
        ? [
            [24, 8, 8, 8, 4, 0],
            [32, 20, 8, 12, 4, 8],
            [52, 20, 4, 12, 0, 8],
            [44, 52, 4, 12, 12, 8],
            [12, 20, 4, 12, 4, 20],
            [28, 52, 4, 12, 8, 20],
          ]
        : [
            [8, 8, 8, 8, 4, 0],
            [20, 20, 8, 12, 4, 8],
            [44, 20, 4, 12, 0, 8],
            [36, 52, 4, 12, 12, 8],
            [4, 20, 4, 12, 4, 20],
            [20, 52, 4, 12, 8, 20],
          ];
      const overlay = reverse
        ? [
            [56, 8, 8, 8, 4, 0],
            [32, 36, 8, 12, 4, 8],
            [52, 36, 4, 12, 0, 8],
            [60, 52, 4, 12, 12, 8],
            [12, 36, 4, 12, 4, 20],
            [12, 52, 4, 12, 8, 20],
          ]
        : [
            [40, 8, 8, 8, 4, 0],
            [20, 36, 8, 12, 4, 8],
            [44, 36, 4, 12, 0, 8],
            [52, 52, 4, 12, 12, 8],
            [4, 36, 4, 12, 4, 20],
            [4, 52, 4, 12, 8, 20],
          ];
      const draw = (p) => {
        const [x, y, w, h, dx, dy] = p;
        if (x + w <= img.width && y + h <= img.height)
          c.drawImage(img, x, y, w, h, dx, dy, w, h);
      };
      pieces.forEach((p, i) =>
        draw(
          !modern && [3, 5].includes(i)
            ? [...pieces[i - 1].slice(0, 4), ...p.slice(4)]
            : p,
        ),
      );
      overlay.forEach((p, i) => {
        if (modern || i === 0) draw(p);
      });
      notify(true);
    };
    img.onerror = () => {
      if (alive) notify(false);
    };
    img.src = source;
    return () => {
      alive = false;
    };
  });
</script>

<canvas bind:this={canvas} width="16" height="32" aria-label={label}></canvas>
