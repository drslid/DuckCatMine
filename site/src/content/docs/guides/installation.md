---
title: Installer en une commande
description: Installer la stack Docker DuckCatMine sur un VPS avec DuckDNS ou votre propre domaine.
sidebar: { order: 2 }
---

## Avant de commencer

**Pour rejoindre la partie : Minecraft Java Edition 26.3 sur PC.** La stack installe Paper build 202 pour cette version. Créez ou sélectionnez un profil **26.3** dans le launcher ; changer de version demande de revérifier les plugins.

- Un **VPS neuf Ubuntu LTS ou Debian**, architecture x86-64. L’installation Docker a été testée sur un hôte Ubuntu ; la matrice complète des distributions reste à compléter.
- **4 Go de RAM au minimum**, 8 Go conseillés pour dix places ; 15 Go de disque libre pour l’installation, davantage pour le monde et les sauvegardes.
- Un accès `sudo`, `curl`, `tar`, `python3` et des certificats système à jour.
- Un compte officiel Minecraft Java pour chaque joueur.
- Un domaine dont les enregistrements A, et AAAA si présent, pointent vers le VPS.
- Les ports **80/tcp, 443/tcp, 25565/tcp** libres et autorisés dans le pare-feu de l’hébergeur. Gardez votre accès SSH.

L’assistant ne modifie pas le pare-feu et ne met pas à niveau votre système. S’il manque Docker, il propose son installation depuis le script officiel Docker. Il refuse de remplacer une installation existante.

## 1. Lancer l’assistant

Dans la console du VPS :

```bash
curl -fsSL https://raw.githubusercontent.com/drslid/DuckCatMine/v0.2.0-alpha.5/install.sh | sudo bash
```

Vous pouvez d’abord [lire le script](https://github.com/drslid/DuckCatMine/blob/v0.2.0-alpha.5/install.sh), le télécharger et l’exécuter ensuite. La commande utilise une version précise.

L’assistant vous demande :

1. D’accepter vous-même [l’EULA Minecraft](https://www.minecraft.net/eula).
2. Le nom du domaine, sans `https://` ni chemin.
3. Les pseudos Java exacts des administrateurs, séparés par des virgules.
4. Le profil **small** ou **friends**.
5. Facultativement, le token DuckDNS pour maintenir l’adresse à jour.

Le token est saisi sans affichage et conservé dans un fichier local privé. Sans token, vous gérez le DNS vous-même. Pour un domaine personnel, aucun service DuckDNS n’est lancé.

## 2. Activer votre compte web

La fin de l’installation affiche l’adresse du panneau et **un lien privé par administrateur**, valable 48 heures. Ouvrez votre lien et choisissez une phrase de passe d’au moins 12 caractères. Ne partagez pas ce lien.

Le premier démarrage du jeu et la création du certificat HTTPS peuvent prendre quelques minutes. Attendez la disponibilité du serveur avant de vous connecter.

Après l’installation, la gestion courante se fait dans le panneau. Le système d’exploitation et les mises à jour de la stack restent de la maintenance serveur.

## 3. Rejoindre la partie

Dans Minecraft Java 26.3 : **Multijoueur → Ajouter un serveur → Adresse** = votre domaine. Le port 25565 est implicite.

Les administrateurs indiqués sont OP et sur la liste blanche. Ajoutez vos amis depuis **Joueurs**. Le statut OP en jeu et l’accès au panneau web sont distincts.

Pour créer ultérieurement un compte web ou renouveler un lien expiré, depuis `/opt/duckcatmine` :

```bash
sudo docker compose exec -T panel node server.js invite PseudoExact
```

## Avec cloud-init

Le fichier [`examples/cloud-init.yaml`](https://github.com/drslid/DuckCatMine/blob/main/examples/cloud-init.yaml) prépare un VPS et exécute le même assistant en mode automatisé. Remplacez les valeurs d’exemple, configurez le DNS, lisez puis acceptez explicitement l’EULA. Ce template n’achète aucun VPS et ne configure pas le DNS de votre fournisseur.

## En cas d’arrêt de l’installation

Les fichiers déjà créés sont conservés pour diagnostic. Ne relancez pas l’assistant par-dessus : lisez les journaux depuis le dossier d’installation.

```bash
cd /opt/duckcatmine
sudo docker compose ps
sudo docker compose logs --tail 100 minecraft panel worker https
```

Si le DNS est incorrect, corrigez ses enregistrements, attendez leur propagation puis relancez `sudo docker compose restart https`. Évitez les tentatives répétées de certificat tant que le domaine pointe ailleurs.
