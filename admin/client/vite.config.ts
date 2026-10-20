import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
export default defineConfig({
  base: './',
  plugins: [svelte()],
  build: { outDir: '../public/v2', emptyOutDir: true, sourcemap: false },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:3000',
      '/icons': 'http://127.0.0.1:3000',
    },
  },
});
