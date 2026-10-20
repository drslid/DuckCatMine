---
title: Gérer une partie
description: Joueurs, carte, objets, skins et constructions dans une interface simple.
sidebar: { order: 3 }
---

## Aider un joueur

Ouvrez **Joueurs**, puis sa fiche. Les informations détaillées sont chargées à la demande. Un joueur doit être connecté pour les actions sur son inventaire, sa santé et sa position.

- **Profil** : soigner, nourrir, changer le mode de jeu, téléporter, régler les droits OP ou la liste blanche.
- **Inventaire** : choisir le sac, l’équipement ou le coffre de l’End. Sélectionner une case pour remplacer, vider ou échanger son contenu.
- **Objets** : chercher un objet par son nom, choisir une quantité, donner ou déposer devant le joueur.
- **Skins** : rechercher, regarder l’aperçu puis appliquer. Le changement est envoyé via SkinsRestorer ; certaines modifications nécessitent une actualisation côté client.

Une modification d’inventaire est refusée si le joueur a déplacé l’objet depuis la lecture de la case. Actualisez la fiche et recommencez.

![Recherche de skins dans la démo](/DuckCatMine/screens/desktop-skins.jpg)

## Poser une construction ou une créature

Dans **Monde**, choisissez **Constructions** ou **Créatures**. Sur téléphone, le catalogue s’ouvre dans un tiroir : sélectionnez un modèle, revenez à la carte puis choisissez le point de pose.

Pour une construction : **Vérifier le terrain → Poser ici → Confirmer**. La pose modifie réellement le monde. L’aperçu est un visuel du modèle ; ce n’est pas une projection bloc par bloc sur le terrain. Une pente excessive ou une zone occupée peut empêcher la pose.

Les fichiers personnels `.schem` sont traités par WorldEdit. Importez des modèles dont vous avez le droit d’usage. Le panneau ne récupère pas les constructions d’autres serveurs sans fichier fourni.

Les trois petits modèles défensifs font **3 × 3 × 6 blocs**, ciblent les monstres dans un rayon de **32 blocs** et ne tirent que dans les zones chargées par le jeu. Les constructions importées ne deviennent pas automatiquement des tours défensives.

## Discuter

Le chat du panneau reçoit les messages du jeu par un flux partagé. Les messages envoyés depuis le web sont identifiés **[Web:Pseudo]**, pour que chacun sache qui parle et d’où.

## Ce qui s’applique immédiatement

La météo, l’heure, certaines règles de jeu, les skins, les objets et les actions sur un joueur sont envoyés sans relancer Minecraft. Les réglages nécessitant un démarrage sont affichés comme **en attente**.

Le redémarrage planifié crée une sauvegarde, puis attend **cinq minutes sans joueur**. Pour l’application immédiate des réglages et les restaurations, tous les joueurs doivent être déconnectés. Aucun bouton ne doit être utilisé comme substitut à une sauvegarde externe.

## Les premières commandes en jeu

| Commande | Usage |
| --- | --- |
| `/guide` | Ouvrir le menu d’accueil. |
| `/spawn` | Revenir au spawn du monde. |
| `/sethome`, `/home`, `/delhome` | Enregistrer, rejoindre, supprimer sa maison. |
| `/construire` | Rejoindre la zone de départ, près du spawn. |
| `/warp` | Voir les destinations. |
| `/setwarp nom`, `/delwarp nom` | Gérer les destinations, pour les OP. |
| `/nouveautes` | Lire les annonces du serveur. |
| `/aider`, `/aides` | Se proposer pour aider et voir les volontaires connectés. |

Le socle public utilise le spawn naturel ; il ne remplace aucun terrain par une place préconstruite.
