---
title: Notifications sur téléphone
description: Installer l’application web et recevoir les messages du chat sur Android et iPhone.
sidebar: { order: 5 }
---

Les notifications utilisent **Web Push**, sans service payant ajouté par DuckCatMine. Elles passent par le service de notification de votre navigateur ou système. Leur livraison dépend aussi des réglages du téléphone.

## iPhone et iPad

1. Utiliser **iOS/iPadOS 16.4 ou plus récent** et ouvrir l’adresse HTTPS dans Safari.
2. Faire **Partager → Sur l’écran d’accueil**.
3. Ouvrir l’application depuis sa nouvelle icône et se connecter.
4. Dans **Chat → Notifications**, toucher **Activer sur ce téléphone** et accepter.
5. Utiliser le test d’affichage, puis le test d’envoi.

L’installation sur l’écran d’accueil est nécessaire pour le [Web Push sur iOS](https://webkit.org/blog/13966/web-push-for-web-apps-on-ios-and-ipados/).

## Android

Ouvrir le panneau HTTPS avec un navigateur compatible, se connecter, puis activer les notifications dans Chat. L’installation proposée par le navigateur donne un accès plus pratique depuis l’écran d’accueil.

## Si le site dit « envoyé » mais rien n’apparaît

Vérifiez les notifications de l’application dans les réglages du téléphone, le mode Concentration/Ne pas déranger, le résumé programmé et les restrictions d’énergie. Refaites les deux tests depuis l’icône installée. Réactivez l’abonnement si vous avez effacé les données du site.

Une réponse HTTP du service push confirme l’acceptation de l’envoi ; elle ne prouve pas que le téléphone a montré l’alerte. Ne partagez pas un compte web avec d’autres administrateurs si vous voulez identifier clairement les messages et abonnements.
