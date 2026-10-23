# DuckCatApocalypse — Ground Zero

Extension optionnelle de DuckCatMine : un scénario de survie horrifique dans un monde dédié, où une morsure laisse 60 secondes pour finir un antidote et où la mort fait perdre tout ce que l'on porte et toute la progression de la vie, pendant que les constructions restent dans le monde.

**État : alpha, jalon J4 « la ville se défend ».** Ce n'est pas encore la v0.1 décrite dans le cahier des charges : voir les tableaux ci-dessous.

## Ce que contient J4

| Fonction | État | Vérification |
|---|---|---|
| Siège : un infecté bloqué sur le chemin d'un survivant frappe l'obstacle (porte, vitre, barricade, mur construit) ; fissures visibles, sons, résistance par matériau (barricade 20, bois 40, maçonnerie 120, métal et barricade renforcée 250), puissance par archétype (errant 3, briseur 14, abomination 25) ; jamais la roche ou la terre naturelles, les conteneurs, les stations, le refuge, ni une base revendiquée dont le propriétaire est absent ; au plus 3 assaillants par bloc et 8 blocs détruits par seconde ; aucun objet lâché | Implémenté | Autotest en situation : porte enfoncée par un errant en 14 coups, roche intacte ; **non testé en jeu** |
| Réparation d'un bloc entamé : clic droit avec des planches (bois, barricade), de la ferraille (métal) ou de la pierre/des briques (maçonnerie), une fois par seconde | Implémenté | **Non testé en jeu** |
| Menace persistante : monte seulement quand quelqu'un joue hors du refuge (3 par heure), jamais pendant l'absence, pas remise à zéro par la mort ; augmente le plafond local et la part d'infectés spéciaux ; sprinteurs dès 1, hurleurs et briseurs à 2, abomination à 4 | Implémenté | Autotest ; **non testé en jeu** |
| Cycle de tension : calme, indices (gémissements lointains), pression, pic avec une horde qui marche vers l'endroit où tu étais, répit ; un tir ou une explosion avance le pic ; à la tombée de la nuit, une horde de plus | Implémenté | **Non testé en jeu** |
| Faune : vaches, moutons et cochons autour des fermes et dans les prairies, meutes de loups en forêt ; un infecté qui blesse un animal peut le contaminer (50 %), incubation visible de 30 à 60 s (particules, plaintes, lenteur), puis l'animal devient hostile et mord comme un infecté ; plus de reproduction ni d'apprivoisement ; 12 animaux infectés au plus, 6 contaminations par 10 min ; l'état survit aux redémarrages | Implémenté | Autotest (conversion d'une vache) ; génération vérifiée sur serveur réel (12 vaches, 25 moutons, 2 loups sur 169 tronçons de campagne) ; **non testé en jeu** |
| Administration : `threat [valeur]`, `destruction on|off` (persistant), `horde <joueur>`, ligne d'état (menace, phase, hordes, siège, incubations) | Implémenté | Serveur réel |

## Ce que contient J3

| Fonction | État | Vérification |
|---|---|---|
| Largage : chaque nouvelle vie (première entrée ou après une mort) commence n'importe où sur la carte (65 % en ville, le reste en campagne), jamais dans le refuge, dans un bâtiment, dans l'eau, près du laboratoire ni à côté d'un autre survivant | Implémenté | Autotest (40 tirages) ; **non testé en jeu** |
| Provisions de départ aléatoires, uniquement des objets courants (les objets rares sont refusés par la configuration) ; 40 % de chances d'avoir de quoi fabriquer une première arme ; rien si la vie précédente a duré moins de 10 min | Implémenté | Tests de configuration ; **non testé en jeu** |
| Fabrication : menu de recettes sur l'établi (avec accès à l'établi classique), l'atelier mécanique et le poste médical ; ingrédients authentifiés, consommés seulement si tout est là ; résultat dans l'inventaire ou au sol ; bruit selon la recette ; catalogue `/apocalypse recipes` | Implémenté | Autotest (15 recettes valides) ; **non testé en jeu** |
| Armes de mêlée : massue, lame, lance (portée 4 blocs), massue renforcée ; dégâts, cadence et usure réelles, barre d'usure visible | Implémenté | Autotest (composants) ; **non testé en jeu** |
| Pistolet de fortune : clic droit pour tirer (tir tracé, arrêté par les blocs), touche F pour recharger (2,5 s, interrompu si on change d'objet), chargeur de 6, munitions fabriquées, usure, cadence, bruit fort ; refusé au refuge | Implémenté | **Non testé en jeu** |
| Réparation à l'atelier (50 % de l'usure contre des composants) | Implémenté | **Non testé en jeu** |
| Barricades (légère, renforcée), piège à pointes (charges, crédite son propriétaire présent), leurre sonore à lancer, balise de base : identité gardée dans le tronçon, rendue à la casse, insensible aux pistons | Implémenté | **Non testé en jeu** ; la casse par les infectés arrive avec J4 |
| Bruit et perception : tir, explosion, course, vitre brisée, coups, fouille, fabrication et leurre attirent les infectés vers l'endroit du bruit ; tout infecté fouille la dernière position où il t'a vu (6 à 15 s selon l'archétype) ; accroupi ou dans le noir sans courir, on n'est repéré que de près | Implémenté | **Non testé en jeu** |

## Ce que contient J2

| Fonction | État | Vérification |
|---|---|---|
| Ground Zero généré : ville de 1 200 blocs (25 × 25 îlots) autour du refuge, rues marquées, voitures, barricades, lampadaires ; puis campagne vallonnée, forêts, lacs, fermes, cabanes, 4 barrages militaires, 4 stations-service et le complexe NEXUS ; bordure de 3 072 blocs | Terminé | Paper 26.3 réel : génération de 2 200 × 2 200 blocs, rendus vus du dessus et en coupe ; tests du plan (déterminisme, aucun bâtiment sur la rue ni superposé) |
| Secteurs : quartier du refuge, résidentiel, centre-ville, zone industrielle, abords de l'hôpital, périphérie, campagne, complexe NEXUS | Terminé | Tests unitaires du plan |
| Bâtiments meublés et fouillables : maisons (1-2 étages), immeubles (3-5 étages, échelles), commerces, supermarchés, pharmacies, garages, entrepôts, hôpital (3 étages, héliport), postes de police (armurerie), ruines, parcs, fermes, laboratoire | Terminé | Autotest : 129 conteneurs générés retrouvés et identifiés sur 8 îlots |
| Fouille : butin par type de lieu (12 tables), tiré à la première ouverture et partagé, jamais retiré à la réouverture ; réapprovisionnement après 30 min de scénario actif, seulement vide et quand plus personne n'est dans les parages ; on ne peut rien y déposer ; conteneurs fixés ; un coffre posé par un joueur ne devient jamais un conteneur de fouille | Implémenté | Autotest (tirage unique, remise à zéro) ; **non testé en jeu** |
| Six archétypes : errant, poursuivant (fouille la dernière position connue), sprinteur (rafale annoncée puis essoufflement), hurleur (cri préparé, interrompu par un coup, attire les voisins, 2 renforts plafonnés), briseur (lent, robuste), abomination (frappe au sol annoncée, longue récupération, une seule en vie) | Implémenté | Autotest : statistiques et vitesses réelles ; **comportements non testés en jeu** |
| 17 apparences indépendantes du comportement (voir le manifeste) | Implémenté | Autotest : 17 apparences sans armure ni effet de jeu |
| Morsure aléatoire : chaque coup reçu a une chance d'être une morsure (25 à 50 % selon l'archétype, environ 1 sur 3 en moyenne) ; signal discret (barre de boss, message court, son léger) | Implémenté | Configuration testée ; **non testé en jeu** |
| Apparitions par secteur : plafond local, mélange d'archétypes, +2 la nuit, toujours au sol (jamais sur un toit), parfois dans un bâtiment abandonné sombre, jamais dans un lieu éclairé ni au refuge | Implémenté | **Non testé en jeu** |
| Infectés protégés du soleil (Paper 26.3 ignore `setShouldBurnInDay`) | Terminé | Autotest |
| Composants : ruban, ressort, pièces d'arme, douilles, charge propulsive, alcool médical, seringue, sérum scellé, inhibiteur ; bandage (rend 2 cœurs, ne soigne pas la morsure) ; notes à lire | Implémenté | Autotest ; les recettes arrivent avec J3 |
| Carte : `/apocalypse admin map [rayon] [y]` écrit `map.png` (vue du dessus, ou coupe à la hauteur y avec les conteneurs en couleur) | Terminé | Serveur réel |
| Configuration version 2 ; une config.yml v1 est mise de côté (`config.v1.yml`), jamais effacée | Terminé | Serveur réel |

## Ce que contient J1

| Fonction | État | Vérification |
|---|---|---|
| Monde dédié `ground_zero`, refuge public muré, règles de jeu propres au monde | Terminé | Paper 26.3 build 202 réel : création, blocs du refuge, redémarrage |
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

Pas encore là : équipes et PvP, compétences, introduction, pack de ressources (J5).

## Règles pour les joueurs

- `/apocalypse join ground_zero` : ton inventaire habituel est mis de côté ; tu es largué quelque part dans Ground Zero avec quelques provisions courantes, parfois de quoi fabriquer une arme.
- Le refuge muré du centre-ville est sûr ; c'est aussi par là qu'on quitte Ground Zero. Dehors, fouille les maisons, commerces, pharmacies, garages, l'hôpital, les postes de police… Chaque lieu a son butin : médical à la pharmacie et à l'hôpital, pièces au garage, munitions à la police et aux barrages. Un coffre vidé se remplit de nouveau plus tard, quand plus personne n'est autour. On y prend, on n'y range pas.
- Fabrique sur un établi (clic droit : recettes du scénario, bouton « Établi classique » pour Minecraft), puis sur un atelier mécanique ou un poste médical que tu poses. `/apocalypse recipes` montre toutes les recettes.
- Pistolet : clic droit pour tirer, touche d'échange de main (F) pour recharger. Chaque tir attire les infectés des environs vers l'endroit du tir.
- Le bruit attire : courir, tirer, casser une vitre, fouiller. Accroupi, ou dans le noir sans courir, tu n'es repéré que de près. Un infecté qui te perd de vue fouille l'endroit où il t'a vu pour la dernière fois.
- Une porte ou un mur ne protègent pas indéfiniment : un infecté bloqué qui te veut frappe l'obstacle jusqu'à le briser (les fissures montrent l'état). Consolide au clic droit avec des planches, de la ferraille ou de la pierre. Une balise protège ta base pendant ton absence, pas quand tu es dedans.
- Plus le monde est joué, plus la menace monte : plus d'infectés autour de toi et plus de spéciaux. Des gémissements lointains annoncent une horde ; un coup de feu la fait venir plus tôt. La nuit, ils sont plus nombreux.
- Méfie-toi des animaux qui traînent, toussent et ralentissent : ils ont été mordus et deviendront hostiles. Leur morsure t'infecte comme celle d'un infecté.
- Les infectés mordent au hasard : **un coup sur trois environ est une morsure**. **Une morsure laisse 60 secondes pour finir un antidote** (clic droit maintenu 2 s). Commencer trop tard ne sauve pas.
- Repère les infectés spéciaux : le sprinteur hurle avant de foncer puis s'essouffle ; le hurleur se fige pour crier (frappe-le pour l'interrompre) ; le briseur est lent mais encaisse ; l'abomination prépare une frappe au sol, écarte-toi.
- À ta mort, ta vie est terminée : objets portés dans un **sac qui reste 5 minutes** là où tu es tombé, XP et progression perdues. Tu es largué ailleurs avec le même nom : il faut retraverser la carte pour récupérer ton sac. Tes constructions et tes coffres restent.
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

Reconstruire Ground Zero (nouvelle génération de la ville, par exemple après une mise à jour du générateur) sans toucher aux joueurs :

1. `/apocalypse admin disable`, puis arrêter Minecraft.
2. Mettre de côté `world/dimensions/minecraft/ground_zero` (le déplacer hors du dossier `data`, ne pas le supprimer tout de suite). Ne jamais toucher à `apocalypse.db` : elle garde les inventaires habituels des survivants déconnectés.
3. Relancer Minecraft, puis `/apocalypse admin world recreate confirm` et `/apocalypse admin enable`.

Les constructions et coffres de l'ancien monde disparaissent avec lui. Chaque survivant garde sa vie et ce qu'il porte, et reprend au refuge à sa prochaine connexion ou entrée.

## Commandes

| Commande | Permission |
|---|---|
| `/apocalypse join [ground_zero]`, `leave`, `status`, `infection`, `bag [open]` | `duckcat.apocalypse.play` (tous) |
| `/apocalypse admin status`, `enable`, `disable`, `reload`, `selftest` | `duckcat.apocalypse.admin` (OP) |
| `/apocalypse admin map [rayon] [y]` : carte `map.png` dans le dossier du plugin (génère les tronçons manquants) | `duckcat.apocalypse.admin` |
| `/apocalypse admin threat [1-50]` (menace), `destruction on|off` (casse de blocs par les infectés), `horde <joueur>` (tracés) | `duckcat.apocalypse.admin` |
| `/apocalypse admin world create`, `world bind <monde> confirm` | `duckcat.apocalypse.admin` |
| `/apocalypse admin give <joueur> <objet> [n]` (tracé ; objets : `antidote`, `bandage`, `scrap`, `cloth`, `tape`, `spring`, `gun_parts`, `casings`, `propellant`, `alcohol`, `syringe`, `serum`, `inhibitor`, `note`) | `duckcat.apocalypse.admin` |
| Traverser la frontière avec son profil habituel, pour observer (tracé) | `duckcat.apocalypse.bypass` (OP) |

## Configuration

`plugins/DuckCatApocalypse/config.yml` (version 2), commenté. Toutes les durées, vitesses, chances de morsure, budgets d'apparition par secteur, capacités des archétypes et tables de butin (infectés et lieux) ont des bornes ; une valeur hors bornes ou une référence inconnue (archétype, apparence, secteur, lieu, objet) est refusée avec son chemin. `reload` garde la configuration précédente si la nouvelle est invalide, et une infection ou un sac en cours garde l'échéance calculée à sa création. Un fichier d'une version précédente est renommé `config.v1.yml` au démarrage et remplacé par la nouvelle version : reporte tes réglages à la main.

## Apparences des infectés (manifeste)

L'apparence ne change jamais les statistiques : elle est tirée parmi celles autorisées pour l'archétype, avec plus de soignants près de l'hôpital, d'ouvriers en zone industrielle et de fermiers à la campagne. Sans pack de ressources, ce sont des zombies vanilla : variante, taille et vêtements teints purement cosmétiques (sans armure).

| ID | Visible sans pack | Limite honnête |
|---|---|---|
| `citizen` | Zombie, t-shirt et pantalon de couleurs variées, taille 0,97-1,03 | Même visage et même peau |
| `citizen_tall` | Manteau long sombre, taille 1,08-1,14 | |
| `citizen_short` | Taille 0,88-0,93 | |
| `child` | Bébé zombie vêtu de couleurs vives, sans le bonus de vitesse vanilla | Pas d'autre morphologie d'enfant |
| `elder` | Gilet beige ou brun, pantalon gris, parfois cheveux gris (casque teint) | Âge seulement suggéré |
| `nurse` | Blouse et pantalon blancs ou bleu clair | |
| `paramedic` | Veste rouge, pantalon bleu marine | |
| `worker` | Gilet orange, pantalon bleu, casque de chantier | |
| `security` | Tenue noire, parfois casquette | |
| `farmer` | Zombie villageois fermier (chapeau de paille), régions variées | |
| `villager` | Zombie villageois de 7 régions et 13 métiers | Diversité d'origines par les régions vanilla, pas des nationalités |
| `drifter` | Husk (peau sable), parfois veste | L'effet de faim du husk est retiré |
| `runner` | Haut de sport coloré, baskets blanches | |
| `howler` | Zombie villageois sans métier (robe verte), un peu plus grand | Signal lisible du hurleur |
| `heavy_worker` | Casque de chantier et plastron de fer, taille 1,15-1,23 | Signal lisible du briseur |
| `heavy_security` | Casque et plastron de fer anti-émeute, tenue noire, taille 1,15-1,23 | Signal lisible du briseur |
| `abomination` | Zombie torse nu de taille 1,6 | Pas de modèle de boss spécifique |

Non réalisé sans pack : visages, teintes de peau, femmes et hommes distincts, corpulences. La diversité visuelle complète demande un pack de ressources qui n'est pas encore livré.

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

43 tests : règles d'infection et d'antidote, horloge, butin garanti, configuration (chances de morsure, secteurs, lieux, références inconnues), plan de la ville (déterminisme, lots, repères, relief), sac, plan de reprise, codage des profils, base SQLite réelle (contraintes, transactions abandonnées, instantané).

`/apocalypse admin selftest` vérifie sur le serveur réel, sans joueur, les API dont dépend le scénario : objets et composants de l'antidote, sérialisation d'un inventaire avec emplacements vides, écriture en base, règles du monde, errant protégé du soleil, chaque archétype (santé, armure, vitesse réelle) et chaque apparence, conteneurs générés conformes au plan de la ville, tirage d'une fouille. Il génère au besoin quelques îlots autour du refuge (une ou deux secondes la première fois).

### Tester sur une instance isolée de ton VPS

`scripts/apocalypse-test.sh` installe une copie de la branche dans `~/duckcatmine-apocalypse-test` (jamais le dossier de production, qu'il refuse), avec ses propres ports en boucle locale et son propre monde. Il crée et ouvre Ground Zero, puis lance l'autotest. Avec ton utilisateur habituel : le script ajoute `sudo` à Docker si nécessaire.

```sh
curl -fsSLo ~/apocalypse-test.sh https://raw.githubusercontent.com/drslid/DuckCatMine/feat/apocalypse/scripts/apocalypse-test.sh
bash ~/apocalypse-test.sh install TonPseudo --accept-eula
```

`--accept-eula` vaut acceptation de l'EULA Minecraft : lis-la avant. Le script affiche ensuite le tunnel SSH à ouvrir depuis le PC qui a Minecraft Java 26.3, puis l'adresse `localhost:25585`. Le tunnel donne aussi le panneau de test sur `http://localhost:18080` ; pour y créer un compte : `docker compose exec -T panel node server.js invite TonPseudo` dans le dossier de test.

Ensuite, depuis `~/duckcatmine-apocalypse-test` :

```sh
scripts/apocalypse-test.sh update     # récupère la dernière version de la branche, reconstruit, relance, autotest
scripts/apocalypse-test.sh logs       # lignes utiles du journal du serveur
scripts/apocalypse-test.sh status     # conteneurs et état du scénario
scripts/apocalypse-test.sh cmd "apocalypse admin status"
scripts/apocalypse-test.sh down       # arrête l'instance de test, données conservées
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

Procédure J2 :

1. Sortir du refuge : rues, voitures, maisons ; les infectés apparaissent au sol, en plein jour sans brûler.
2. Ouvrir un coffre d'une maison : son nom indique le lieu, le butin apparaît ; refermer et rouvrir : même contenu ; tenter d'y déposer un objet : refusé ; le casser : refusé.
3. Se laisser toucher plusieurs fois : toutes les attaques ne mordent pas ; à la morsure, barre de boss et message court, sans titre plein écran.
4. Approcher un sprinteur, un hurleur et un briseur (centre-ville, hôpital, zone industrielle) et vérifier leurs signaux.
5. Utiliser un bandage : 2 cœurs rendus, la morsure continue.

## Limites connues

- La durée d'administration de l'antidote est comptée en ticks par le client et le serveur : un serveur ralenti l'allonge, l'échéance reste en temps réel.
- Les objets tenus au curseur ou dans la grille de fabrication au moment de la mort tombent au sol comme en vanilla.
- Les progrès et statistiques Minecraft restent communs aux deux profils.
- Les infectés repèrent encore les survivants avec la détection vanilla (portée de poursuite) ; le bruit, l'accroupissement et l'obscurité ne font que la retarder ou la limiter.
- Les infectés n'ouvrent pas les portes : ils les enfoncent (siège), ce qui prend du temps.
- La faune n'apparaît que dans les tronçons de campagne générés après la mise à jour J4 (le centre déjà généré est urbain et n'en avait pas).
- Les infectés ne ciblent pas un joueur en mode créatif ou spectateur (règle du serveur) : tester en survie.
- Les étages des immeubles et de l'hôpital se parcourent par des échelles ; les apparitions intérieures se font au rez-de-chaussée.
- Le monde est généré à la volée : la première visite d'une zone coûte un peu plus au serveur qu'un monde plat.
