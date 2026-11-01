# Ground Zero · pack original

Six silhouettes originales (cracheur, bondisseur, rampant, traqueur, boursouflé et briseur), textures de 32 pixels et six sons synthétisés. Aucun asset de Minecraft ni modèle téléchargé n’est redistribué. Licence GPL-3.0-only, comme le projet.

Le pack prêt à servir se trouve dans `admin/public/packs/`, avec son manifeste SHA-1. Il cible **Minecraft Java 26.3, format de ressources 97.1**. Les miniatures du panneau sont calculées à partir des mêmes modèles et textures.

Pour reconstruire les assets : Python 3.13 et FFmpeg avec libvorbis, puis depuis la racine :

```sh
python3 resource-pack/build.py --out admin/public/packs
python3 resource-pack/test_pack.py
```

L’installation n’a pas besoin de FFmpeg : les assets sont déjà fournis. La génération écrit aussi des miniatures dans `admin/public/horror/`. `preview.json` sert à la revue locale de la géométrie et n’est pas exposé par le serveur HTTP.

L’installateur HTTPS écrit `data/plugins/DuckCatApocalypse/resource-pack.json` :

```json
{"url":"https://votre-domaine.example/packs/duckcatmine-horror-51d517b28e20bfcb.zip","sha1":"51d517b28e20bfcb18f3ba4488fc942c709aa904"}
```

Pour une installation existante, reprends le chemin et le SHA-1 du manifeste, puis relance le plugin avec une sauvegarde du monde. Seul le ZIP vérifié est public, avec cache immuable et `noindex`. Les données du jeu et les API d’administration restent authentifiées.

Le pack est ajouté individuellement, sans remplacer les autres packs. Refus ou échec de chargement : apparence native. La validation serveur teste les structures des assets et les entités d’affichage ; le rendu final reste à vérifier avec un client 26.3.
