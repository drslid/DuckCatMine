---
title: Sauvegardes et maintenance
description: Sauvegarder, restaurer et préparer une mise à jour de DuckCatMine.
sidebar: { order: 6 }
---

## Sauvegarder le monde

Les sauvegardes quotidiennes couvrent le dossier de données Minecraft et sont conservées sept jours. **Réglages → Sauvegardes → Sauvegarder** lance une copie cohérente avec le jeu, puis permet de télécharger l’archive.

Conservez aussi des copies **hors du VPS**. Une archive sur le même disque ne protège pas contre la perte du serveur.

## Restaurer

Déconnectez les joueurs, choisissez l’archive dans le panneau et confirmez avec votre mot de passe et le texte demandé. Le worker arrête le jeu, garde une copie de l’état précédent, restaure, puis redémarre.

Les chemins sortant du dossier de données et les liens dans les archives sont refusés. Cela ne dispense pas de vérifier l’origine d’une sauvegarde.

La stack inclut les fichiers JAR des extensions dans ses nouvelles sauvegardes. Une ancienne archive qui ne contient pas toutes les extensions installées est refusée avant l’arrêt du jeu ; sa récupération exige une restauration manuelle conservant les bons fichiers d’extensions.

## Ce qu’il faut conserver pour récupérer toute la stack

| Chemin | Contenu |
| --- | --- |
| `data/` | Mondes, inventaires, extensions et paramètres Minecraft. |
| `admin/state/` | Comptes web hachés, audit et abonnements push. |
| `.env` et `secrets/` | Domaine, configuration et secrets locaux. |
| `backups/` | Archives du jeu. |
| Volumes Caddy | État HTTPS, régénérable avec un DNS correct. |

Les sauvegardes Minecraft ne couvrent pas les comptes web et secrets. Sauvegardez ces derniers séparément dans un emplacement privé. Ne les joignez jamais à une issue GitHub.

## Mettre à jour cette alpha

L’installation refuse volontairement d’écraser un monde existant. Il n’existe pas encore de mise à jour automatique avec migration et retour arrière garantis.

Avant de changer de version : sauvegardez le monde et les fichiers ci-dessus, lisez les notes de version, testez la nouvelle version dans une installation séparée, puis planifiez la migration. Ne remplacez pas la version du jeu au hasard : un monde ouvert par une version plus récente peut ne plus être compatible avec l’ancienne.

## Arrêter sans effacer

```bash
cd /opt/duckcatmine
sudo docker compose down
```

Les dossiers de données restent présents. N’ajoutez pas `--volumes` si vous souhaitez garder l’état Caddy.
