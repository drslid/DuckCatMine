---
title: Découvrir DuckCatMine
description: Une administration visuelle pour votre petit serveur Minecraft Java entre amis.
sidebar: { order: 1 }
---

DuckCatMine réunit un serveur Minecraft Paper, un panneau web et les services utiles pour jouer entre amis. Le panneau fonctionne sur ordinateur et téléphone. Le code est ouvert ; vos mondes et vos comptes restent sur votre machine.

[**Essayer la démo →**](/DuckCatMine/demo/) · [Installer mon serveur](/DuckCatMine/guides/installation/)

La démo simule Alex, Sam et Robin. Ses actions restent dans le navigateur ; elle ne se connecte à aucun serveur de jeu.

![Accueil de la démo DuckCatMine](/DuckCatMine/screens/desktop-home.jpg)

## Cinq endroits, un rôle chacun

| Onglet | À quoi il sert |
| --- | --- |
| Accueil | Vérifier que la partie va bien, voir les joueurs et accéder aux actions courantes. |
| Joueurs | Choisir un joueur, modifier son inventaire, son skin, son mode de jeu ou ses droits. |
| Monde | Une seule carte, les joueurs en direct, les constructions et les créatures. |
| Chat | Lire, répondre avec son pseudo web et régler les notifications. |
| Réglages | Gérer la partie, les sauvegardes, les performances et son compte. |

Le bouton de chat reste accessible dans tous les onglets. Une pastille signale les messages non lus.

## Ce qui est fourni

- Minecraft Java **26.3**, Paper **build 202**, Java 25.
- Interface Svelte, API Node.js et worker de maintenance séparé.
- BlueMap 5.29, SkinsRestorer 15.12.6 et WorldEdit 7.4.6.
- DuckCatWelcome : guide d’accueil, maisons, warps et aide entre joueurs.
- DuckCatTowers : petits modèles défensifs et import `.schem`.
- HTTPS avec Caddy, sauvegardes quotidiennes, application web installable.

Les extensions tierces sont téléchargées depuis leurs sources officielles pendant l’installation, avec vérification des sommes de contrôle. Les textures des objets sont générées localement à partir du client officiel, après acceptation de l’EULA Minecraft.

## Portée de cette version alpha

Cette version vise **un serveur Java privé** et un petit groupe de confiance. Les comptes du panneau disposent de droits d’administration complets. Le pseudo web sert d’identifiant ; le mot de passe web est indépendant du compte Microsoft.

Le mode Bedrock, les réseaux de plusieurs serveurs, le SSO Microsoft, les rôles web fins et une place de marché d’extensions ne sont pas intégrés. mCraft reste une extension facultative ; les trains ne font pas partie du socle public.

Le dépôt fournit un exemple cloud-init. Aucune image DuckCatMine n’est actuellement publiée dans les places de marché des hébergeurs.
