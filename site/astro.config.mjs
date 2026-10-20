import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://drslid.github.io',
  base: '/DuckCatMine',
  trailingSlash: 'always',
  vite: {
    plugins: [{
      name: 'demo-directory-index',
      configureServer(server) {
        server.middlewares.use((req, _res, next) => {
          if (req.url === '/DuckCatMine/demo/' || req.url === '/demo/') req.url += 'index.html';
          next();
        });
      },
    }],
  },
  integrations: [
    sitemap(),
    starlight({
      title: 'DuckCatMine',
      description: 'Un serveur Minecraft entre amis, une administration visuelle, une installation Docker guidée.',
      defaultLocale: 'root',
      locales: { root: { label: 'Français', lang: 'fr' } },
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/drslid/DuckCatMine' }],
      sidebar: [{ label: 'Le guide', items: [{ autogenerate: { directory: 'guides' } }] }],
      customCss: ['./src/styles/docs.css'],
      editLink: { baseUrl: 'https://github.com/drslid/DuckCatMine/edit/main/site/' },
    }),
  ],
});
