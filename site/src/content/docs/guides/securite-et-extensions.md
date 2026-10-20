---
title: Accès et extensions
description: Comprendre les accès administrateurs, le réseau et les extensions de DuckCatMine.
sidebar: { order: 8 }
---

## Un panneau privé

Le panneau et la carte nécessitent une session. Les réponses privées demandent aux robots de ne pas indexer le service. Cette consigne ne remplace pas l’authentification.

La vitrine GitHub Pages et sa documentation sont publiques et indexables. Elles ne contiennent ni adresse de serveur personnel, ni monde, ni identifiants.

## Des droits complets pour les administrateurs

Un compte web dispose de la console, des inventaires et des opérations de sauvegarde. Réservez-le aux personnes de confiance. Utilisez une phrase de passe distincte de votre compte Minecraft/Microsoft.

Les comptes OP en jeu et les comptes web sont séparés. Retirer OP à un joueur ne révoque pas son compte web. Pour révoquer un accès web dans cette alpha, arrêtez le panneau, sauvegardez puis retirez ce compte de `admin/state/users.json`, et relancez le panneau pour fermer ses sessions. Ne publiez jamais ce fichier.

## Les services exposés

Seuls HTTPS/HTTP et Minecraft sont publiés. RCON et BlueMap restent dans le réseau Docker. Le panneau web n’a pas accès au socket Docker ; seul le worker de maintenance y accède. Ce socket lui donne un pouvoir important sur l’hôte : ne montez pas ce service sur un serveur partagé sans examiner ce modèle de confiance.

Les actions web sont journalisées ; les écritures exigent un jeton CSRF. Les sessions expirent et les connexions sont limitées en fréquence. Ces protections ne constituent pas un audit de sécurité indépendant.

## Extensions facultatives

mCraft peut ajouter des objets personnalisés. Installez une version compatible depuis l’auteur officiel, vérifiez sa licence et sauvegardez avant de redémarrer Minecraft. La rubrique du panneau indique quand l’extension est absente. Aucun JAR mCraft ou modèle tiers n’est redistribué dans le dépôt.

Les fichiers `.schem`, textures de skins et modèles ont leurs propres droits d’usage. La licence de DuckCatMine ne vous donne pas de droits supplémentaires sur ces contenus.

La première version publique cible la combinaison de versions épinglées. Changer la version de Minecraft ou d’une extension nécessite un test de compatibilité.
