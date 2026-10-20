---
title: Comprendre les performances
description: Réduire la charge CPU et mémoire de Minecraft et de BlueMap.
sidebar: { order: 4 }
---

## Lire les bons chiffres

Le bandeau en haut du panneau résume la santé : **vert** si le jeu tourne normalement, **orange** si la fluidité baisse ou les ressources se tendent, **rouge** si la partie ralentit fortement, le disque manque de place ou Minecraft ne répond plus. Un état **gris** signifie que les mesures manquent ou sont anciennes. Le texte rend l’état lisible sans dépendre seulement de la couleur.

Le **CPU** du bandeau est la consommation de Minecraft rapportée à tous les vCPU du VPS ; ce n’est pas la charge totale de tous ses services. La **RAM** indique la mémoire du conteneur Minecraft et sa limite Docker. Un clic ouvre **Réglages → Performances**. Les mesures proviennent du flux partagé déjà utilisé par le panneau.

Dans Docker, **100 % de CPU correspond à un cœur utilisé**. Une valeur supérieure à 100 % peut donc être normale sur une machine à plusieurs cœurs. Pour juger la fluidité, regardez aussi les TPS et le temps par tick : le budget à 20 TPS est de 50 ms par tick.

La mémoire du conteneur comprend le tas Java, des données natives et les ressources des extensions. Elle ne redescend pas nécessairement dès qu’un joueur se déconnecte. Réduire arbitrairement le tas peut augmenter le travail du ramasse-miettes et provoquer des ralentissements.

## Réglage recommandé

**Réglages → Performances → Priorité au jeu** : BlueMap redessine le terrain quand il n’y a aucun joueur. Les positions restent en direct. Le terrain peut donc être en retard sur les constructions récentes jusqu’à la fin de la partie.

Le socle limite le rendu à un thread. C’est conforme aux [conseils de configuration BlueMap](https://bluemap.bluecolored.de/wiki/getting-started/Configuration.html).

| Profil | Tas Java maximal | Limite du conteneur | Vue / simulation | Places |
| --- | --- | --- | --- | --- |
| Small | 2 Go | 3 Go | 6 / 4 chunks | 6 |
| Friends | 4 Go | 6 Go | 8 / 6 chunks | 10 |

Les places sont une limite de connexion, pas une garantie de performances. L’exploration rapide, les fermes d’entités, la redstone et les extensions peuvent changer fortement la charge.

## Pourquoi le panneau est léger

- Interface Svelte compilée ; les outils lourds se chargent à l’ouverture de leur onglet.
- Une seule carte montée à la fois, uniquement dans Monde.
- Flux SSE partagé pour le chat et l’état ; repli vers des lectures espacées si le flux est interrompu.
- État du serveur en cache 5 secondes ; métadonnées stables en cache 60 secondes.
- Les navigateurs simultanés partagent la même collecte RCON.
- Lecture incrémentale du journal et hachage asynchrone des mots de passe.

Le [protocole de mesure](https://github.com/drslid/DuckCatMine/blob/main/docs/verification.md) explique les résultats et leurs limites. Il ne s’agit pas d’un comparatif de performances entre hébergeurs.
