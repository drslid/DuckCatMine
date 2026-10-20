import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
export default defineConfig(({ mode }) => ({
  base: './',
  plugins: [svelte(), {
    name: 'demo-head',
    transformIndexHtml(html) {
      if (mode !== 'demo') return html;
      return html.replace(/<link\s+rel="manifest"[^>]*>/g, '')
        .replace('<title>DuckCatMine</title>', '<title>DuckCatMine · Démo</title>');
    },
  }],
  build: { outDir: '../public/v2', emptyOutDir: true, sourcemap: false },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:3000',
      '/icons': 'http://127.0.0.1:3000',
    },
  },
}));
