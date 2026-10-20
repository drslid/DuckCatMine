---
title: Choisir un VPS abordable
description: Profils jusqu’à 100 joueurs, budgets et offres économiques OVHcloud, Hetzner, Contabo et netcup.
sidebar: { order: 7 }
---

Commencez avec un serveur proche des joueurs, un SSD et un processeur rapide. Pour une petite partie, un grand nombre de vCPU lents n’aide pas autant qu’un cœur performant.

## De votre groupe d’amis à une communauté

Les budgets mensuels ci-dessous sont des **fourchettes de préparation**, fondées sur la sélection d’offres vérifiée le **20 octobre 2026**, pour des emplacements européens et une TVA française approximative. Ce ne sont pas des moyennes statistiques du marché. Les tarifs les plus bas peuvent demander une promotion ou un paiement anticipé long.

| Objectif de joueurs simultanés | CPU / RAM du VPS | Stockage de départ | Budget hébergement / mois |
| --- | --- | --- | --- |
| 6 — profil Small | 2 vCPU rapides / 4 Go | SSD 40 Go | 4–8 € |
| 10 — profil Friends | 4 vCPU rapides / 8 Go | SSD 80 Go | 4–15 € |
| 25 — configuration manuelle | 4 vCPU rapides dédiés / 16 Go | NVMe 120 Go | 35–55 € |
| 50 — configuration manuelle | 6 vCPU rapides dédiés / 16 Go | NVMe 160 Go | 35–75 € |
| 75 — configuration manuelle | 8 vCPU rapides dédiés / 24 Go | NVMe 240 Go | 67–100 € |
| 100 — configuration manuelle | 8–12 vCPU rapides dédiés / 32 Go | NVMe 320 Go | 67–120 € |

La RAM correspond à la machine entière. Gardez une marge pour Java hors du tas mémoire, la carte, le panneau et les sauvegardes. Le [README détaille les budgets Java et Docker](https://github.com/drslid/DuckCatMine#vps-sizing-from-a-few-friends-to-100-concurrent-players).

**Ces capacités restent à valider en charge.** Les grands paliers n’ont pas été testés avec autant de joueurs. L’installateur propose Small et Friends ; le réglage du panneau est limité à 50 places. Au-delà, il faut une configuration manuelle et le fonctionnement complet du panneau à cette échelle reste à valider. Pré-générez un monde borné, surveillez les entités et les fermes, puis augmentez progressivement la fréquentation. Consultez aussi le [guide des performances](/DuckCatMine/guides/performances/).

## Offres économiques chez des hébergeurs connus

Sélection sans affiliation parmi **Contabo, OVHcloud, Hetzner et netcup**. Les liens renvoient aux sites officiels. Choisissez **x86-64** pour cette version de DuckCatMine.

| Offre | Configuration | Repère mensuel et conditions |
| --- | --- | --- |
| [Contabo Cloud VPS 4](https://contabo.com/en-us/vps/) | 4 vCPU partagés, 8 Go, SSD 100 Go | **4,40 € en promotion**, tarif habituel affiché 5,50 €. TVA incluse selon l’affichage ; promotion annoncée pour les 24 premiers mois. Vérifiez la TVA locale, la durée et les frais d’installation ou de localisation au paiement. |
| [OVHcloud VPS-1](https://www.ovhcloud.com/fr/vps/) | 2 vCores, 4 Go, NVMe 40 Go | Dès **4,57 € TTC français** ; le lien public du configurateur sélectionne un prépaiement de 12 mois. |
| [Hetzner CX23](https://www.hetzner.com/cloud/cost-optimized/) | 2 vCPU partagés, 4 Go, NVMe 40 Go | Environ **7,19 € TTC français avec IPv4** ; facturation horaire plafonnée au mois, disponibilité à vérifier. |
| [OVHcloud VPS-2](https://www.ovhcloud.com/fr/vps/) | 4 vCores, 8 Go, NVMe 75 Go | Dès **8,65 € TTC français**, prépaiement de 12 mois dans le lien public. Stockage légèrement sous le budget de départ de 80 Go. |
| [Hetzner CX33](https://www.hetzner.com/cloud/cost-optimized/) | 4 vCPU partagés, 8 Go, NVMe 80 Go | Environ **10,79 € TTC français avec IPv4** ; facturation horaire plafonnée au mois, disponibilité à vérifier. |
| [netcup RS 2000 G12.5 — 24M EU](https://www.netcup.com/en/server/root-server/rs-2000-g12.5-iv-24m-eu) | 8 cœurs dédiés, 16 Go, NVMe 256 Go | Environ **34,89 € TTC français**, calculés depuis 34,60 € avec TVA allemande à 19 %. **Engagement et facturation de 24 mois**, localisation européenne automatique, IPv4 incluse. |
| [netcup RS 4000 G12.5 — 24M EU](https://www.netcup.com/en/server/root-server/rs-4000-g12.5-iv-24m-eu) | 12 cœurs dédiés, 32 Go, NVMe 512 Go | Environ **66,72 € TTC français**, calculés depuis 66,16 € avec TVA allemande à 19 %. **Engagement et facturation de 24 mois**, localisation européenne automatique, IPv4 incluse. |
| [Hetzner EX44-1-LTD](https://www.hetzner.com/dedicated-rootserver/ex44/) | Machine dédiée i5-13500, 64 Go, 2 × 512 Go NVMe | Environ **70,80 € TTC français avec IPv4**, offre LTD à stock limité, installation affichée à 0 €. Vérifiez que cette variante précise est disponible. |

Sources des prix : [configuration OVHcloud prépayée 12 mois](https://www.ovhcloud.com/fr/vps/configurator/?brick=VPS%2BModel%2B1&planCode=vps-2027-model1&pricing=upfront12&processor=+&storage=40__SSD__NVMe&vcore=2__vCore), [tarifs Contabo avec TVA](https://contabo.com/en-us/pricing/), [barème Hetzner en vigueur](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) et [suppléments IPv4](https://docs.hetzner.com/general/infrastructure-and-availability/ipv4-pricing/), ainsi que les fiches netcup liées dans le tableau. Calcul Hetzner : `(tarif HT + IPv4) × 1,20` ; estimation netcup : `total allemand ÷ 1,19 × 1,20`. Le panier fixe le prix final, le renouvellement et les disponibilités.

Pour commencer au prix le plus bas, comparez **Contabo en promotion et OVHcloud VPS-1**. Pour dix places, comparez **Contabo, OVHcloud VPS-2 et Hetzner CX33**. Pour les objectifs de 25 à 100 joueurs, regardez les **netcup RS à CPU dédié**, puis les machines de l’[enchère Hetzner](https://www.hetzner.com/sb/), dont les tarifs et le matériel changent. Un VPS partagé avec beaucoup de RAM ne remplit pas à lui seul la recommandation CPU des grands paliers. Aucun de ces hébergeurs n’a été testé par DuckCatMine aux grands nombres de joueurs annoncés comme objectifs.

Comparez le coût **total** : hébergement, IPv4, installation, sauvegarde externe et éventuellement domaine personnel. Les budgets du premier tableau excluent la sauvegarde externe, le domaine et les frais d’installation. DuckDNS permet d’éviter l’achat d’un domaine. Minecraft Java et le VPS restent à votre charge.

Un VPS Docker préinstallé peut faire gagner une étape. Le template cloud-init du dépôt automatise la préparation ; il ne constitue pas une intégration officielle à une place de marché.

## Éviter les économies trompeuses

Ne choisissez pas 1 ou 2 Go de RAM pour la stack complète. Gardez de l’espace pour les sauvegardes et le rendu de carte. Avant de souscrire pour un an, essayez une courte période et regardez les TPS et temps par tick pendant une vraie partie.
