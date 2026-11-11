# Jouer dans le navigateur

L’accès web est une option expérimentale pour **Minecraft Java 26.3**. Le lanceur exécute le jeu dans un navigateur compatible WebGPU. Depuis l’accueil du panneau, **Jouer dans le navigateur** ouvre le lanceur et **Copier le lien** permet de partager son adresse.

Le même lien sert à tous les joueurs. Le lanceur vérifie la licence Java et l’UUID Minecraft, puis consulte la liste blanche de Paper. Ajoute ou retire un joueur depuis l’onglet **Joueurs**. Un retrait bloque les prochaines connexions par le lanceur. Les sessions de jeu sont séparées des comptes d’administration.

## Essai

1. Choisis un nom HTTPS distinct du panneau, par exemple `jouer.example.com`. Fais pointer ce nom vers le Caddy de DuckCatMine.
2. Ajoute `WEB_PLAY_HOST=jouer.example.com` dans le fichier `.env` de l’installation de test. Garde l’adresse Java habituelle dans `MC_ADDRESS`. Le relais présente cette adresse au client et joint Paper sur le réseau Docker.
3. Depuis le dossier DuckCatMine, utilise Docker Compose 2.17 ou plus récent et un builder compatible avec les contextes Git.

   ```bash
   docker compose -f compose.yaml -f compose.webplay.yaml up -d --build panel webplay https
   ```

4. Ouvre **Accueil → Jouer dans le navigateur**. Dans le lanceur, importe le fichier `accounts.json` du compte Java actif dans [Prism Launcher](https://prismlauncher.org/wiki/getting-started/data-location/). Clique sur **Install** une fois, puis **Play**. Le lanceur passe l’adresse de DuckCatMine à Minecraft au démarrage.

L’import se fait sur ton instance HTTPS. Le lanceur reçoit un jeton Minecraft temporaire et le garde en mémoire pendant la session. Réserve cet essai à une instance et à des joueurs de confiance. Après expiration ou redémarrage du lanceur, actualise ton compte dans Prism et importe-le à nouveau.

Teste avec un compte sur la liste blanche et un second compte Java qui en est absent. Pour le compte autorisé, vérifie l’arrivée dans le monde, le déplacement, l’inventaire, le chat, la reconnexion et les packs de ressources éventuels avant de partager le lien.

## Désactivation

```bash
docker compose -f compose.yaml -f compose.webplay.yaml stop webplay
docker compose up -d panel https
```

Le premier lancement utilise les fichiers de Minecraft téléchargés depuis Mojang par le navigateur. La surcouche utilise les sources et le runtime [minecraft.wasm.click](https://github.com/vano04/minecraft.wasm.click) sous GPL-3.0. Les révisions du lanceur et du relais sont épinglées dans [compose.webplay.yaml](../compose.webplay.yaml). Le test intégré utilise des comptes fictifs et un relais local.
