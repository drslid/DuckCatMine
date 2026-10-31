# DuckCatApocalypse — Ground Zero

Extension optionnelle de DuckCatMine : un scénario de survie horrifique dans un monde dédié, où une morsure laisse 60 secondes pour finir un antidote et où la mort fait perdre tout ce que l'on porte et toute la progression de la vie, pendant que les constructions restent dans le monde.

**État : alpha, jalon J5 « ensemble ou seul ».** Ce n'est pas encore la v0.1 décrite dans le cahier des charges : voir les tableaux ci-dessous.

## Mise à jour survie — alpha.6

- **Carte → touche le sol → Me téléporter ici** : le compte web déplace son personnage après confirmation et vérification du sol. Chargement asynchrone ; inventaire, vie et progression conservés.
- **Un départ équipé** : chaque nouvelle vie reçoit une arme de mêlée aléatoire, 4–6 aliments, 2 bandages, 8 torches, une pioche et des provisions aléatoires. Une reconnexion ne redonne pas le kit. Les vies existantes reçoivent ce kit une seule fois.
- **Fouille plus généreuse** : 5–9 piles dans les maisons et boutiques, 7–12 au supermarché, 4–8 à la police et à l'entrepôt lorsque les anciens réglages par défaut étaient utilisés. Les anciens conteneurs de fouille reçoivent une seule fois 3 piles supplémentaires dans leurs emplacements libres. Les coffres des joueurs sont exclus.
- **Trois armes à feu sans balles** : pistolet au clic droit ; fusil à pompe à courte portée ; mitraillette en maintenant le clic droit. Une usure par tir, y compris une gerbe complète. Réparation à l'atelier avant la casse ; règles de PvP et du refuge respectées. Visuels Minecraft vanilla, aucun pack ni mod obligatoire.
- **Sonar personnel** : carte en seconde main (F), cercle de 10 blocs orienté selon le regard. Survivants en bleu, infectés et joueurs mordus proches en rouge ; joueurs lointains sur l'anneau extérieur bleu. `/apocalypse radar` récupère une carte perdue. Sur le panneau, sélectionne un survivant pour son sonar (positions toutes les 5 secondes).
- **Maisons et magasins** : pièces meublées, cuisines, salles de bain, escaliers d'immeuble et rayons contenant du stock à prendre au clic droit. Les nouvelles zones utilisent ces modèles dès leur génération.
- **Rénovation de l'existant** : Ground Zero → Maintenance. Zone limitée et scénario vide ; préparation asynchrone des chunks déjà générés seulement. Pause tant qu'un joueur est dans ce monde. Seuls les blocs encore identiques au modèle d'origine sont remplacés ; conteneurs, ajouts et bases revendiquées conservés. Un ancien coffre peut donc rester à son emplacement d'origine.

La conversion des anciens tronçons de rue utilise une file bornée qui accepte les chargements de chunks déclenchés pendant une conversion, sans invalider son parcours.

La migration copie `config.yml`, ajoute les recettes et conserve les autres réglages personnalisés. Les tests Paper vérifient le kit, le radar contextuel, les plans dans quatre orientations, les rayons, les tirs bloqués par un mur, les arrivées sûres et la conservation d'un ajout et du contenu d'un coffre. La sensation de tir automatique et le rendu de la carte en seconde main restent à confirmer avec un client Minecraft.

## Ce que contient J5

| Fonction | État | Vérification |
|---|---|---|
| Équipes : création, invitation cliquable (2 min), acceptation/refus, départ (le chef passe la main), retrait par le chef, dissolution, liste avec l'état de chacun ; 4 membres au plus ; propriété par UUID ; la mort ne retire pas de l'équipe | Implémenté | Tests de la base (unicité du nom, une équipe par joueur) ; **non testé en jeu** |
| PvP désactivé par défaut, activable (`/apocalypse admin pvp on`) ; jamais entre coéquipiers ni au refuge, tirs et projectiles compris ; règles annoncées à l'entrée et à chaque changement | Implémenté | Commande testée sur serveur réel ; **combat non testé en jeu** |
| Bases : dans le rayon d'une balise, les autres survivants ne peuvent ni casser, ni poser, ni ouvrir les conteneurs posés ou les stations (les coffres de fouille de la ville restent ouverts à tous) ; les infectés n'assiègent pas une base dont toute l'équipe est absente | Implémenté | **Non testé en jeu** |
| Sacs : récupérables par les coéquipiers si le chef l'autorise (`/apocalypse team bags on|off`, autorisé par défaut), jamais par les autres | Implémenté | **Non testé en jeu** |
| Antidote injecté à un autre survivant mordu : accroupi, clic droit sur lui antidote en main, 2 s sans s'éloigner ; une dose consommée seulement si la guérison est validée ; ne ranime jamais une vie terminée | Implémenté | **Non testé en jeu** |
| Points d'une vie partagés selon les dégâts réellement infligés dans les 30 s (pas de dégâts sur un infecté déjà mort, ni de coups annulés) ; 3 compétences à 3 rangs : endurance (+3 % de vitesse par rang), mêlée (+8 % de dégâts), souffle (respiration sous l'eau) ; appliquées seulement dans Ground Zero, retirées à la sortie, perdues à la mort ; `/apocalypse stats` (vie et carrière) et `/apocalypse skills` | Implémenté | Tests (rangs bornés, coûts, points refusés à une vie morte) ; **non testé en jeu** |
| Introduction d'environ 50 s à la première entrée (NEXUS-13, alarme, rupture, radio du refuge, réveil, « Survis jusqu'à la prochaine aube. ») dans le refuge protégé, puis largage ; ignorable (`/apocalypse intro skip`), rejouable depuis le refuge sans morsure, reprise si on se déconnecte pendant ; les survivants qui avaient déjà joué ne la voient pas d'office | Implémenté | Test de migration ; **non testé en jeu** |

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
| Armes à feu : pistolet, fusil à pompe et mitraillette, sans munitions, avec usure ; refusées au refuge | Implémenté | Rayons et composants vérifiés sur Paper ; contrôles client à confirmer |
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

Pas encore là : pack de ressources (modèles d'objets dédiés), scènes d'introduction avec caméra.

## Règles pour les joueurs

- `/apocalypse join ground_zero` : ton inventaire habituel est mis de côté ; tu es largué quelque part dans Ground Zero avec quelques provisions courantes, parfois de quoi fabriquer une arme.
- Le refuge muré du centre-ville est sûr ; c'est aussi par là qu'on quitte Ground Zero. Dehors, fouille les maisons, commerces, pharmacies, garages, l'hôpital, les postes de police… Chaque lieu a son butin : médical à la pharmacie et à l'hôpital, pièces au garage, munitions à la police et aux barrages. Un coffre vidé se remplit de nouveau plus tard, quand plus personne n'est autour. On y prend, on n'y range pas.
- Fabrique sur un établi (clic droit : recettes du scénario, bouton « Établi classique » pour Minecraft), puis sur un atelier mécanique ou un poste médical que tu poses. `/apocalypse recipes` montre toutes les recettes.
- Pistolet : clic droit pour tirer, touche d'échange de main (F) pour recharger. Chaque tir attire les infectés des environs vers l'endroit du tir.
- Le bruit attire : courir, tirer, casser une vitre, fouiller. Accroupi, ou dans le noir sans courir, tu n'es repéré que de près. Un infecté qui te perd de vue fouille l'endroit où il t'a vu pour la dernière fois.
- Une porte ou un mur ne protègent pas indéfiniment : un infecté bloqué qui te veut frappe l'obstacle jusqu'à le briser (les fissures montrent l'état). Consolide au clic droit avec des planches, de la ferraille ou de la pierre. Une balise protège ta base pendant ton absence, pas quand tu es dedans.
- Plus le monde est joué, plus la menace monte : plus d'infectés autour de toi et plus de spéciaux. Des gémissements lointains annoncent une horde ; un coup de feu la fait venir plus tôt. La nuit, ils sont plus nombreux.
- Méfie-toi des animaux qui traînent, toussent et ralentissent : ils ont été mordus et deviendront hostiles. Leur morsure t'infecte comme celle d'un infecté.
- À plusieurs : `/apocalypse team create <nom>`, puis `invite`. Pas de dégâts entre coéquipiers ; un coéquipier peut reprendre ton sac si le chef l'autorise, et n'importe quel survivant peut t'injecter un antidote (accroupi, clic droit). Le PvP est désactivé sauf annonce contraire.
- Chaque infecté éliminé rapporte des points à ceux qui l'ont blessé. Dépense-les en compétences (`/apocalypse skills`) : endurance, mêlée, souffle. Tout est perdu à la mort.
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
| `/apocalypse recipes [objet]`, `stats`, `skills [up <endurance|melee|breath>]`, `intro [skip]` | `duckcat.apocalypse.play` |
| `/apocalypse team create <nom>`, `invite <joueur>`, `accept`, `decline`, `leave`, `kick <joueur>`, `bags on|off`, `disband`, `info` | `duckcat.apocalypse.play` |
| `/apocalypse admin status`, `enable`, `disable`, `reload`, `selftest` | `duckcat.apocalypse.admin` (OP) |
| `/apocalypse admin map [rayon] [y]` : carte `map.png` dans le dossier du plugin (génère les tronçons manquants) | `duckcat.apocalypse.admin` |
| `/apocalypse admin threat [1-50]` (menace), `destruction on|off` (casse de blocs par les infectés), `horde <joueur>`, `pvp on|off` (tracés) | `duckcat.apocalypse.admin` |
| `/apocalypse admin world create`, `world bind <monde> confirm` | `duckcat.apocalypse.admin` |
| `/apocalypse admin give <joueur> <objet> [n]` (tracé ; objets : `antidote`, `bandage`, `scrap`, `cloth`, `tape`, `spring`, `gun_parts`, `casings`, `propellant`, `alcohol`, `syringe`, `serum`, `inhibitor`, `note`) | `duckcat.apocalypse.admin` |
| Traverser la frontière avec son profil habituel, pour observer (tracé) | `duckcat.apocalypse.bypass` (OP) |

## Panneau web

Le plugin écrit toutes les 5 s `plugins/DuckCatApocalypse/status.json` (état, menace, règles, infectés par type, survivants présents avec leur position, équipes) ; le panneau DuckCatMine le lit en lecture seule et affiche l'onglet **Ground Zero** dès que ce fichier existe :

`/apocalypse join` ouvre le choix de départ en jeu : solo, salle d'attente commune, ou départ près d'un survivant déjà présent. `/apocalypse group [pseudo]` rejoint cette salle ; `/apocalypse start` lance le compte à rebours commun de 5 secondes. Les participants apparaissent à des positions distinctes autour d'un point commun. Les inventaires classiques ne sont mis de côté qu'à la fin du compte à rebours. Une vie déjà sauvegardée garde son inventaire et sa progression ; choisir le groupe change explicitement sa position de reprise. Le départ solo conserve l'introduction et la reprise habituelles. `/apocalypse leave` retire un joueur de la salle d'attente. Une déconnexion ne relance pas le compte à rebours des autres ; un groupe vide ou une préparation annulée ne peut pas déclencher une entrée tardive.

Le panneau affiche le groupe en attente et permet de lancer ou d'annuler son départ. Sur une fiche de survivant, **Rejoindre ce joueur** déplace le survivant sélectionné près de la destination choisie après confirmation. `/apocalypse admin teleport <joueur> <survivant>` offre la même action en jeu ; elle est réservée à deux survivants présents, cherche une place libre et sûre dans des chunks chargés, conserve les morsures et trace le déplacement. Aucun changement de monde ni sol dangereux accepté.

Les voitures suivent la direction du regard avec une vitesse de rotation limitée ; les touches gauche/droite restent utilisables, avancer/reculer, saut (frein), sprint (klaxon) et Maj (descendre) gardent leurs rôles. La rotation des sièges invisibles reste fixe pour préserver la caméra. Seuls les mouvements synchrones des sièges enregistrés sont admis par le garde de téléportation. Une voiture en mouvement ne donne plus d'immunité aux attaques : les protections normales du refuge et du début de vie continuent de s'appliquer.

- **Carte** : BlueMap intégré comme dans Monde, avec zoom, déplacement, rotation et positions des joueurs en direct. Cliquer sur un joueur ouvre ses actions ; « Voir ce joueur » recentre la carte sans la recharger. Le détail des survivants est actualisé toutes les 5 s. Une vue légère du dessus reste disponible si BlueMap manque ou si l'appareil préfère l'image. Un seul affichage de carte est monté à la fois ; les changements d'onglet retirent le lecteur 3D. Le catalogue visuel donne les objets Ground Zero avec une quantité ; une horde ou une évacuation demandent confirmation. Le journal, les équipes et le classement sont repliables.
- **Règles** : préréglages Détendu, Normal et Cauchemar ; menace, PvP, destruction et les 19 réglages du catalogue, groupés par thème et modifiables à chaud. Les morsures et sacs déjà en cours gardent leurs échéances.
- **Maintenance** : diagnostic, autotest, rechargement, rendu de carte avec coupe en hauteur facultative, actualisation des rues autour du refuge et accès aux sauvegardes. Commandes avancées de création, association et recréation d'un monde absent. La recréation devient disponible après mise à l'écart de l'ancien dossier, serveur arrêté.
- **Nouvelle partie** : termine les vies et expire les sacs, avec confirmation saisie et refus tant qu'un survivant est présent. Le terrain et les inventaires classiques sont conservés.

Chaque action passe par une liste fermée de commandes `/apocalypse admin …`, avec authentification, CSRF, contrôle des types, bornes et confirmations côté API, puis trace dans le journal du panneau. Le plugin vérifie de nouveau le joueur et le monde au moment d'agir. Les refus du jeu apparaissent comme des erreurs dans le site. Les dons administrateurs utilisent le catalogue dédié ; les dons et modifications d'inventaire ordinaires restent protégés pour les survivants. Un `status.json` de plus de 30 s désactive les actions. Le suivi lit le fichier partagé et ne déclenche aucune commande RCON ; l'écran Ground Zero est chargé à la demande.

Sur téléphone, le chat reste accessible dans la barre de navigation avec sa pastille. La bulle flottante est masquée dans Ground Zero pour laisser les actions entièrement visibles. Les champs mobiles utilisent une police de 16 px.

**Infectés sur la carte** : le bouton « Infectés » affiche ou masque les créatures actives, en 3D et dans la vue légère. Leur type apparaît au survol ; les abominations ont un repère violet. Le choix est conservé sur l'appareil. Le plugin publie au plus 512 positions toutes les 5 s, uniquement à partir des créatures déjà suivies et chargées : aucun tronçon n'est chargé pour le suivi. Le panneau réutilise sa lecture d'état existante, sans requête RCON supplémentaire. Les repères 3D sont déplacés en place, retirés quand la créature disparaît et vidés si l'état devient périmé ou si l'affichage est désactivé. Les zones déchargées ne sont donc pas représentées comme une carte complète des monstres du monde.

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
- Une mort en attente est finalisée avant une sortie ou une correction de frontière. Une seconde notification de réapparition ne réinitialise pas à nouveau l'inventaire. La base refuse d'enregistrer un profil de retour pour une vie terminée, une ancienne vie ou un autre joueur ; la transaction annulée conserve le profil classique.
- L'horloge du scénario est enregistrée toutes les 5 s : après un arrêt brutal, au plus 5 s sont rendues aux joueurs.
- Les sauvegardes DuckCatMine archivent l'instantané `backup/apocalypse-snapshot.db` pris pendant leur `save-all`, pas la base vive. Après une restauration, l'instantané devient la base. Si le monde et la base ne correspondent pas, le scénario se suspend.
- Ne supprime jamais `apocalypse.db` à la main.
- Les ajouts de J5 (tables `teams` et `team_members`, colonnes de points, compétences et introduction) sont additifs : un JAR précédent ignore ces données, le retour arrière par simple remplacement du JAR reste possible. La menace, le PvP et la destruction de blocs sont dans la table `meta`.

## Tests

```sh
mvn -B -f apocalypse/pom.xml verify   # Java 25 ; ou l'image maven:3.9.11-eclipse-temurin-25
```

72 tests Java : infection et antidote, horloge, butin, configuration et réglages à chaud, plan de la ville, conduite à la souris, départ groupé (annulation, déconnexion, préparation tardive), progression, sacs, reprise, profils et SQLite réel. La régression de sortie après un décès vérifie que le profil classique reste intact et qu'une vie terminée ne devient pas un profil de retour.

Les tests Node `admin/apocalypse.test.js` et `admin/smoke-test.js` vérifient les commandes du panneau, les bornes des 19 réglages, l'authentification, le CSRF, les confirmations, les états périmés et les refus du plugin. Les tests HTTP utilisent un serveur RCON simulé. La vérification de l'interface utilise la démo fictive ; un essai sur téléphone physique reste distinct d'une vérification de largeur dans Chromium.

`admin/map-picker.test.js` vérifie le pont BlueMap : messages limités au parent de même origine, coordonnées finies, caméra conservée, sélection des joueurs et maintien de l'éditeur Monde existant. Il vérifie aussi le recyclage et le retrait des repères infectés, le masquage, la limite de 512 positions et les libellés échappés. La carte privée reste protégée par `/api/map-auth`. Le réglage « priorité au jeu » continue de suspendre le rendu du terrain pendant qu'un joueur est connecté ; consulter la carte ne lance aucun rendu ni commande RCON.

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
