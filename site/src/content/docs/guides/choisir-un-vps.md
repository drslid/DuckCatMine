---
title: Choisir un VPS abordable
description: Dimensionnement et budget pour un petit serveur Minecraft auto-hébergé.
sidebar: { order: 7 }
---

Commencez avec un serveur proche des joueurs, un SSD et un processeur rapide. Pour une petite partie, un grand nombre de vCPU lents n’aide pas autant qu’un cœur performant.

## Deux configurations cibles

| Usage | Configuration | Profil |
| --- | --- | --- |
| Quelques amis, peu d’extensions | 2 vCPU, 4 Go RAM, SSD 40 Go ou plus | Small, 6 places maximum |
| Jusqu’à dix places, marge pour explorer | 4 vCPU, 8 Go RAM, SSD 60–80 Go ou plus | Friends |

Les tailles sont des points de départ. Elles ne garantissent pas dix joueurs simultanés avec de grosses fermes ou beaucoup d’exploration.

## Pistes à comparer

Relevé indicatif du **20 octobre 2026**, sans affiliation commerciale. Les tarifs varient avec la région, l’engagement, la fiscalité et les promotions.

- **[OVHcloud VPS](https://www.ovhcloud.com/fr/vps/)** : la page française affiche un VPS-1 à partir de **4,57 € TTC/mois**, 2 vCores, 4 Go RAM, 40 Go NVMe ; le VPS-2 à partir de **8,65 € TTC/mois**, 4 vCores, 8 Go RAM, 75 Go NVMe. Vérifiez les conditions exactes avant commande.
- **[Hetzner Cloud](https://www.hetzner.com/cloud/)** : comparer une offre x86 avec 4 ou 8 Go de RAM. Le prix final dépend notamment de la région et de l’IPv4. Aucun prix fixe n’est repris ici car le configurateur est dynamique.

Comparez le coût **total** : VPS, IPv4, sauvegarde externe et éventuellement domaine personnel. DuckDNS permet d’éviter l’achat d’un domaine. Minecraft Java et le VPS ne deviennent pas gratuits avec DuckCatMine.

Un VPS Docker préinstallé peut faire gagner une étape. Le template cloud-init du dépôt automatise la préparation ; il ne constitue pas une intégration officielle à une place de marché.

## Éviter les économies trompeuses

Ne choisissez pas 1 ou 2 Go de RAM pour la stack complète. Gardez de l’espace pour les sauvegardes et le rendu de carte. Avant de souscrire pour un an, essayez une courte période et regardez les TPS et temps par tick pendant une vraie partie.
