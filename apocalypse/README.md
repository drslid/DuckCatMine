# DuckCatApocalypse — Ground Zero

Extension optionnelle de DuckCatMine : un scénario de survie horrifique dans un monde dédié, où une morsure laisse 60 secondes pour finir un antidote et où la mort fait perdre tout ce que l'on porte et toute la progression de la vie, pendant que les constructions restent dans le monde.

**État : alpha, jalon J1 « survie centrale ».** Ce n'est pas encore la v0.1 décrite dans le cahier des charges : voir le tableau ci-dessous.

## Ce que contient J1

| Fonction | État | Vérification |
|---|---|---|
| Monde dédié `ground_zero` (sol plat, grille de rues), refuge public muré, règles de jeu propres au monde | Terminé | Paper 26.3 build 202 réel : création, blocs du refuge, redémarrage |
| Entrée/sortie avec inventaire séparé (objets, armure, main secondaire, XP, effets, santé, faim, position, mode de jeu) | Implémenté | Journal et reprise testés unitairement ; **non testé en jeu** |
| Frontière : téléportations de plugins, `/home`, `/spawn`, `/warp`, boutons du guide, portails, coffre de l'Ender, lit, mode de jeu | Implémenté | **Non testé en jeu** |
| Morsure : 60 s en temps réel, barre de boss, alertes à 30/10/5 s, morsures suivantes sans effet sur le compte à rebours | Implémenté | Règles testées unitairement ; **non testé en jeu** |
| Antidote NEXUS : 2 s, sans ralentissement, une dose consommée seulement si la guérison est validée | Implémenté | **Non testé en jeu** (composants `consumable`/`use_effects` à confirmer avec un client) |
| Mort : fin de la vie, sac de 5 min au lieu de la mort ou à la dernière position sûre, nouvelle vie au refuge avec la même identité | Implémenté | Base testée unitairement ; **non testé en jeu** |
| Mort par infection hors ligne, reprise à la reconnexion | Implémenté | **Non testé en jeu** |
| Errant (marche lente, renforts/ramassage/conversions/combustion désactivés) et butin : au moins un objet par élimination admissible | Implémenté | Tirage testé unitairement ; **non testé en jeu** |
| Protection de départ de 30 s, terminée à la première attaque | Implémenté | **Non testé en jeu** |
| Horloge du scénario figée serveur arrêté ou scénario suspendu | Terminé | Tests unitaires + redémarrage réel |
| Sauvegarde cohérente : instantané de la base pendant le `save-all` des sauvegardes, base vive exclue, reprise depuis l'instantané après restauration | Terminé | Serveur réel : cycle save-off/save-all/save-on puis restauration simulée |
| Panneau : don, chute d'objets, horde, pluie d'objets, soin, nourriture, XP et modification d'inventaire refusés pour un joueur dans Ground Zero | Terminé | Test de fumée du panneau |
| Configuration invalide : règles de référence appliquées et signalées ; base inaccessible : mode sûr | Implémenté | Configuration invalide testée sur serveur réel ; mode sûr non testé |

Pas encore dans J1 : fouille et conteneurs, fabrication, pistolet, autres infectés, bruit et perception, sièges, faune contaminée, directeur de menace complet, équipes et PvP, compétences, introduction, pack de ressources, statut Apocalypse dans le panneau, balise de base. Ils suivent dans les jalons J2 à J4.

## Règles pour les joueurs

- `/apocalypse join ground_zero` : ton inventaire habituel est mis de côté ; tu arrives au refuge sans rien la première fois.
- Le refuge est sûr. Dehors, les infectés mordent : **une morsure laisse 60 secondes pour finir un antidote** (clic droit maintenu 2 s). Commencer trop tard ne sauve pas.
- À ta mort, ta vie est terminée : objets portés dans un **sac qui reste 5 minutes** là où tu es tombé, XP et progression perdues. Tu reviens au refuge avec le même nom. Tes constructions et tes coffres restent.
- `/apocalypse bag` donne la position et le temps restant (aucune téléportation) ; `/apocalypse bag open` ouvre ton sac à portée si son apparence a disparu.
- `/apocalypse leave` depuis le refuge, jamais avec une morsure. Se déconnecter ne stoppe pas l'infection.

## Installation et retour arrière

Le module est construit avec les autres plugins mais installé seulement sur demande. Pour une installation neuve, ajoute `--apocalypse` à `scripts/setup.py`. Pour une installation existante :

1. Crée une sauvegarde complète depuis le panneau et télécharge l'archive hors du VPS.
2. Dans le dossier d'installation : ajoute `DUCKCAT_APOCALYPSE=1` à `.env`, puis `docker compose run --build --rm plugins`.
3. Relance Minecraft (relance sauvegardée depuis le panneau).
4. En console ou en jeu (OP) : `/apocalypse admin world create`, puis `/apocalypse admin enable`.

Le monde est créé une seule fois sous `world/dimensions/minecraft/ground_zero` ; aucun monde existant n'est modifié et un nom déjà pris est refusé.

Revenir en arrière :

- **Suspendre** : `/apocalypse admin disable`. Les joueurs présents retrouvent leur survie habituelle ; les états (sacs, infections, inventaires) sont conservés et les horloges figées.
- **Désinstaller** : garder le plugin installé, même suspendu, tant que `/apocalypse admin status` indique des inventaires classiques gardés par le scénario. Chaque survivant hors ligne récupère le sien à sa prochaine connexion ; quand le compteur vaut 0, le JAR peut être retiré.
- **Tout annuler** : restaurer depuis le panneau la sauvegarde créée avant l'installation (ce qui s'est passé depuis est perdu).

## Commandes

| Commande | Permission |
|---|---|
| `/apocalypse join [ground_zero]`, `leave`, `status`, `infection`, `bag [open]` | `duckcat.apocalypse.play` (tous) |
| `/apocalypse admin status`, `enable`, `disable`, `reload`, `selftest` | `duckcat.apocalypse.admin` (OP) |
| `/apocalypse admin world create`, `world bind <monde> confirm` | `duckcat.apocalypse.admin` |
| `/apocalypse admin give <joueur> <antidote\|scrap\|cloth> [n]` (tracé) | `duckcat.apocalypse.admin` |
| Traverser la frontière avec son profil habituel, pour observer (tracé) | `duckcat.apocalypse.bypass` (OP) |

## Configuration

`plugins/DuckCatApocalypse/config.yml`, commenté. Toutes les durées, vitesses, budgets d'apparition et tables de butin ont des bornes ; une valeur hors bornes est refusée avec son chemin. `reload` garde la configuration précédente si la nouvelle est invalide, et une infection ou un sac en cours garde l'échéance calculée à sa création.

## Données et reprise après incident

- Base SQLite `plugins/DuckCatApocalypse/apocalypse.db` (pilote fourni par Paper), un seul fil d'écriture.
- Chaque transition d'un joueur (entrée, sortie, mort, retrait d'un objet du sac) est journalisée avant d'agir en jeu ; le fichier joueur enregistre ensuite le numéro de la dernière opération appliquée. À la reconnexion, une opération interrompue est confirmée, annulée ou rejouée selon ce numéro : jamais d'objet perdu en silence ni dupliqué.
- Une vie ne peut finir qu'une fois (contrainte en base), une seule infection active par vie, un objet ne quitte le sac qu'une fois.
- L'horloge du scénario est enregistrée toutes les 5 s : après un arrêt brutal, au plus 5 s sont rendues aux joueurs.
- Les sauvegardes DuckCatMine archivent l'instantané `backup/apocalypse-snapshot.db` pris pendant leur `save-all`, pas la base vive. Après une restauration, l'instantané devient la base. Si le monde et la base ne correspondent pas, le scénario se suspend.
- Ne supprime jamais `apocalypse.db` à la main.

## Tests

```sh
mvn -B -f apocalypse/pom.xml verify   # Java 25 ; ou l'image maven:3.9.11-eclipse-temurin-25
```

37 tests : règles d'infection et d'antidote, horloge, butin garanti, configuration, sac, plan de reprise, codage des profils, base SQLite réelle (contraintes, transactions abandonnées, instantané).

`/apocalypse admin selftest` vérifie sur le serveur réel, sans joueur, les API dont dépend le scénario : objets et composants de l'antidote, sérialisation d'un inventaire avec emplacements vides, écriture en base, règles du monde, apparition et réglage d'un errant.

### Tester sur une instance isolée de ton VPS

Jamais dans le dossier de production : un clone neuf, ses propres ports en boucle locale, son propre monde.

```sh
git clone --branch feat/apocalypse --single-branch https://github.com/drslid/DuckCatMine.git ~/duckcatmine-apocalypse-test
cd ~/duckcatmine-apocalypse-test
python3 scripts/setup.py --local --apocalypse --admins TonPseudo --profile small --accept-eula --non-interactive --no-invites
docker compose logs -f minecraft            # attendre « Done », puis Ctrl+C
docker compose exec -T minecraft rcon-cli "apocalypse admin world create"
docker compose exec -T minecraft rcon-cli "apocalypse admin enable"
docker compose exec -T minecraft rcon-cli "apocalypse admin selftest"
```

`--accept-eula` vaut acceptation de l'EULA Minecraft : lis-la avant. `setup.py` refuse de démarrer si les ports 18080, 18443 ou 25585 sont déjà pris (par exemple par une ancienne instance de test).

Depuis le PC qui a Minecraft Java 26.3, garde ce tunnel ouvert pendant la partie, puis connecte-toi à `localhost:25585` :

```sh
ssh -N -L 25585:127.0.0.1:25585 -L 18080:127.0.0.1:18080 utilisateur@ton-vps
```

Le second port donne le panneau de test sur `http://localhost:18080` ; pour y créer un compte : `docker compose exec -T panel node server.js invite TonPseudo`.

Mettre à jour après une correction, lire les journaux, arrêter :

```sh
cd ~/duckcatmine-apocalypse-test
git pull --ff-only && docker compose run --build --rm plugins && docker compose restart minecraft
docker compose logs --tail=400 minecraft | grep -iE 'apocalypse|exception|error'
docker compose down
```

Pour raccourcir un test, modifie `data/plugins/DuckCatApocalypse/config.yml` (par exemple `bag-lifetime-seconds: 60`) puis `/apocalypse admin reload`.

Procédure manuelle J1 sur une instance isolée, avec un client Minecraft Java 26.3 :

1. Entrer : refuge, inventaire vide ; ressortir depuis le refuge : inventaire habituel intact.
2. Laisser approcher un errant : protection de départ, puis fin à la première attaque.
3. Être mordu : `MORSURE — ANTIDOTE : 01:00` ; une deuxième morsure ne remet pas à 01:00.
4. `/apocalypse admin give <joueur> antidote 2` : boire guérit et consomme une dose ; boire sain ne consomme rien.
5. Laisser le compte à rebours finir : mort, sac au lieu de la mort, nouvelle vie au refuge.
6. Reprendre une partie du sac : le temps restant continue ; laisser expirer : contenu perdu.
7. Tuer un errant : au moins un objet tombe.
8. Depuis Ground Zero : `/spawn`, `/home`, `/warp`, coffre de l'Ender refusés.
9. Se déconnecter mordu, revenir après la fin du compte : mort hors ligne, sac à la dernière position sûre.
10. Redémarrer le serveur pendant une infection : le compte reprend où il en était.

## Limites connues

- La durée d'administration de l'antidote est comptée en ticks par le client et le serveur : un serveur ralenti l'allonge, l'échéance reste en temps réel.
- Les objets tenus au curseur ou dans la grille de fabrication au moment de la mort tombent au sol comme en vanilla.
- Les progrès et statistiques Minecraft restent communs aux deux profils.
- Apparences : zombies vanilla adultes avec quelques vêtements teints ; la diversité visuelle annoncée viendra avec J2.
