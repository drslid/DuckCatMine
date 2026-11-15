# DuckCatApocalypse — Ground Zero

Extension optionnelle de DuckCatMine : un scénario de survie horrifique dans un monde dédié, où une morsure laisse 60 secondes pour finir un antidote et où la mort fait perdre tout ce que l'on porte et toute la progression de la vie, pendant que les constructions restent dans le monde.

**État : alpha, jalon J5 « ensemble ou seul ».** Ce n'est pas encore la v0.1 décrite dans le cahier des charges : voir les tableaux ci-dessous.

## Chiens et 4x4 — alpha.19

- **Chiens de compagnie** : de temps en temps, un chien errant apparaît dans les rues à une vingtaine de blocs de toi. Donne-lui de la viande (crue ou cuite, même de la chair putréfiée) ou un os : au bout de quelques bouchées il t'adopte. Il te suit, attaque les infectés à moins de 12 blocs, et les infectés ne l'attaquent jamais (ni ne le contaminent). Clic droit : il reste à cet endroit ; clic droit à nouveau : il te suit.
- **4x4** : un véhicule garé sur six (hors camionnettes), 4 places, abrite comme une voiture, roule à pleine vitesse hors des routes et franchit des marches d'un bloc et demi, comme un cheval. Les rues déjà visitées en reçoivent à leur prochain chargement.
- **Bestiaire du panneau** : chaque infecté a désormais son portrait, y compris l'errant, le poursuivant, le sprinteur, le hurleur, le loup, la bête infectée, le Némésis et le Souriant.

## Radar net, motos, discrétion — alpha.18

- **Radar plus net** (pack 1.5.0) : flèches, « N », points et ta propre flèche sont dessinés trois fois plus finement que la carte. Les flèches vers les autres joueurs (**violet**) et vers les caisses (blanc) sont **à l'extérieur du cercle**, le « N » aussi. Points : joueurs connectés en violet, **survivants à sauver en bleu**, infectés en rouge, boss en rose, caisses en blanc. Sous la jauge, en vert pâle : **ping et temps de jeu** de la session (le serveur ne peut pas connaître les FPS du client).
- **Motos réparées pour de bon** : garées, elles étaient entourées de blocs invisibles qui cachaient leur zone de clic ; on ne pouvait pas monter dessus. Elles ne sont plus solides à l'arrêt, et les motos collées à une camionnette sont reculées.
- **Voiture bloquée** : un joueur debout à côté des portières immobilisait la voiture ; seul ce qui se trouve devant (ou derrière en marche arrière) l'arrête maintenant. Clic droit avec un jerrican sur un véhicule plein (ou avec un jerrican vide) : on monte au lieu d'être bloqué dehors.
- **Sac de mort : 10 minutes** au lieu de 5 pour revenir chercher ses affaires.
- **Discrétion** : les infectés ne remarquent un survivant silencieux que devant eux (champ de vision de 130°) ; on les entend marcher et courir, jamais accroupi. Hors de vue et sans bruit pendant 30 s, ils abandonnent et repartent ailleurs.
- **Double-clic** sur un objet d'un coffre ou d'un conteneur : il va directement dans l'inventaire.
- **Torche en main** (ou lanterne, dans l'une ou l'autre main) : elle éclaire autour de toi, mais dans le noir les infectés te voient aussi.

## Radar GPS, discrétion et camionnettes — alpha.16

- **Radar GPS** (pack 1.4.0) : la carte en main disparaît (elle est retirée des inventaires). En haut à droite, un radar rond, au pixel près, tourne avec toi : **le haut est la direction où tu regardes**, un « N » rouge sur le bord indique le nord. Il couvre **128 blocs** autour de toi : relief, routes, toits, eau et arbres en couleur ; infectés en rouge (boss en rose), survivants en bleu, coffres de fouille pas encore ouverts en vert, caisses en blanc. Des **flèches sur le bord** pointent vers chaque survivant connecté à Ground Zero et vers les caisses hors de portée ; sous le radar, **chrono et distance** de la caisse la plus proche.
- **Jauge de repérage** sous le radar : vide quand aucun infecté ne t'a remarqué, verte puis orange puis rouge quand ils sont nombreux à te voir ou à fouiller l'endroit où ils t'ont entendu. Pleine : cache-toi. Sans le pack, la même jauge s'affiche en barre de boss.
- **Largages** : plus de barre permanente ; seul le message radio s'affiche quand la caisse tombe (puis le radar donne la direction).
- **Eau en bleu** : avec le pack, la barre d'hydratation est bleue et sans chiffre vert ; le pack est retiré en quittant Ground Zero, la barre d'expérience redevient verte ailleurs.
- **À l'abri en voiture** : dans une voiture ou une camionnette, seuls le briseur, l'abomination et le Némésis peuvent encore te blesser. La moto ne protège pas.
- **Camionnettes** : une place de stationnement sur quatre, 4 places (2 dans une voiture). Le premier monté conduit. Plus lente, gros réservoir, consomme plus. Dans les rues déjà visitées, une voiture garée sur quatre devient une camionnette au prochain chargement.
- **Motos corrigées** : la détection des obstacles utilisait le gabarit d'une voiture, une moto garée derrière une voiture restait bloquée. Les autotests conduisent désormais voiture, moto et camionnette jusqu'au mur.
- **Plus d'essence (alpha.17)** : voitures et camionnettes garées contiennent deux fois plus d'essence (4 à 28 L), celles déjà garées voient leur réservoir doublé une fois, et elles consomment deux fois moins (camionnette : 0,7 fois la consommation de base). Les motos ne changent pas.

## Survie étendue — alpha.15

**Ils sont plus nombreux.** Densité ×3,5 (au lieu de ×2,5), plafond de 200 infectés, 4 renforts par passage ; réglable sur le site (Ground Zero → Règles). Discrétion : **20 secondes** sans être vu ni entendu et ils passent leur chemin ; la marche s’entend à 8 blocs, la course à **32 blocs**. Seules les anciennes valeurs de référence sont remplacées : un réglage personnalisé reste tel quel.

| Nouveauté | Comment ça marche |
|---|---|
| Munitions | Pistolet, fusil à pompe et mitraillette partagent les **balles** ; le lance-roquettes utilise des **roquettes**. Une arme trouvée contient 30 à 100 balles (5 à 20 roquettes). **F** recharge depuis l’inventaire (100 balles ou 20 roquettes au plus). Balles surtout à la police, à l’armurerie et aux barrages militaires. Les armes déjà possédées reçoivent une fois leur chargement ; les anciens lance-roquettes gardent leurs tirs restants. |
| Portée ×2 | Pistolet 80 blocs, fusil à pompe 36, mitraillette 64, lance-roquettes 192. |
| Soif | La barre d’expérience devient la jauge d’hydratation (100 = désaltéré). Elle se vide en ~35 min de jeu (environ trois vagues), plus vite en courant. Bouteille d’eau +50 (la bouteille vide se remplit à un point d’eau, +35), lait +20. Sous 25 : lenteur ; sous 10 : faiblesse ; à sec : dégâts. |
| Lit | Clic droit sur un lit de Ground Zero : ta prochaine vie commence à côté, s’il est encore debout. On ne dort pas (le cycle jour/nuit continue). |
| Réveil et minuteur | Leurres à lancer. Accroupi + clic droit pour régler le délai (réveil : 30 s à 5 min, très fort ; minuteur : 10 s à 1 min, tic-tac puis sonnerie). Ils se ramassent ensuite. Trouvés dans les maisons et magasins. |
| Ravitaillement | Toutes les 5 à 15 minutes de jeu, message radio militaire et caisse parachutée à 60–150 blocs d’un survivant : **carré blanc** sur la carte du site, barre blanche et chrono dans le jeu. **10 minutes** pour l’ouvrir, sinon elle disparaît. |
| Némésis | Toutes les **10 vagues** (pics de horde et hordes de la nuit), il arrive à 200 blocs d’un survivant et traque le plus proche. Près de 3 blocs de haut, ne saute pas, casse murs, portes et barricades, **tue d’un coup**, rugit pour appeler les infectés. Un survivant qui court le distance. Environ **5 roquettes** pour l’abattre. Barre de vie rouge à moins de 128 blocs. |
| Le Souriant | Nouvel infecté lent à la tête énorme et au sourire diabolique : la nuit, on ne voit presque que son sourire lumineux (sans pack : une tête de citrouille flottante). **Tue en deux coups**, l’armure ne protège pas. |
| Hurleurs | Trois cris : appel de renforts, alerte qui porte deux fois plus loin et avance le prochain pic, cri strident qui assombrit la vue et ralentit les survivants proches. |
| Survivants | Un appel au talkie toutes les ~15 min : un survivant attend à 40–90 blocs (4 au plus par joueur). Clic droit pour le recruter, puis clic droit pour ses missions. **Ingénieur** : fabrique une arme, des balles ou des roquettes avec ce que tu déposes dans son sac (45 s). **Scientifique** : antidote ou bandages. **Garde** : se poste où tu es et tire sur les infectés avec l’arme de la 1re case de son sac (balles prises dans son sac). **Explorateur** : part 5 minutes et ramène du matériel (risque de morsure ou de disparition). « Me suivre / Rester ici », « Renvoyer ». Un survivant **mordu** (40 % le sont déjà à l’appel) refuse toute mission : soigne-le avec un antidote dans les 5 minutes, sinon il devient un infecté vêtu de son métier. Il peut aussi mourir. |
| Infectés de métier | Des errants et poursuivants portent la tenue d’ingénieur, de scientifique, de garde ou d’explorateur. |
| Cartes au trésor | Trois **fragments** (fouilles) forment une carte : tiens-la en main pour la direction et la distance. La cache, marquée d’un X rouge à la campagne, contient une **tourelle laser d’argent** (bleue, 16 blocs, munitions infinies) ou, plus rarement, **d’or** (violette, 26 blocs, plus puissante), et un gros butin. |
| Chevaux | Sellés, dans les bois hors de la ville : monte et pars. Un cheval mordu meurt après une courte agonie, il ne se transforme jamais. |
| Motos | Une place de stationnement sur cinq a une moto derrière la voiture : 2 places, 25 % plus rapide, petit réservoir, frugale. Jerricans pleins dans les maisons, garages et stations-service. |
| Prison, bunkers, caves | Une **prison** au nord-ouest de la ville (armurerie : armes, munitions, armures), cinq **bunkers** cachés à la campagne (trappe en fer, armurerie, lits) et des **caves** sous un tiers des maisons (trappe dans le sol). Construits une fois, à l’approche d’un survivant, sans régénérer le monde. |
| Police | Plus d’armures (mailles, fer) et beaucoup de balles. |
| Radar en haut à droite | Avec le pack : mini-carte colorée en haut à droite de l’écran, nord en haut. Infectés en points rouges, survivants en bleu, coffres de fouille pas encore ouverts en vert. Sans le pack, le sonar en main reste disponible. |
| Bestiaire plus détaillé | Côtes, colonne vertébrale, mâchoires, vêtements déchirés, sang et yeux lumineux sur les mutants ; textures retravaillées. |

Administration : `/apocalypse admin nemesis|drop|survivor <joueur>`, `/apocalypse admin site [id]`, et les mêmes boutons sur la fiche d’un survivant du site. Nouveaux réglages dans **Règles → Survie** (soif, lit, largages, Némésis, survivants) et **Faune** (chevaux). La migration de contenu 8 sauvegarde `config.yml`, ajoute les nouveaux butins, recettes (roquette) et infectés, sans toucher aux réglages personnalisés. Le pack passe en **1.3.0** (~340 Ko) : nouveaux modèles, sons (rugissement du Némésis, rire du Souriant), skins des survivants, police du radar.

À vérifier avec un client Minecraft : rendu du radar (position selon la taille de l’interface), skins des survivants, sourire du Souriant la nuit. Les autotests serveur couvrent le Némésis (5 à 6 roquettes), les caisses, les survivants (sac, morsure, transformation) et les munitions.

## Sens de marche des mutants — alpha.14

Le rendu compense le demi-tour ajouté par le client Minecraft aux entités d’affichage d’objets. La correction porte sur le maillage, sans retourner les pivots ni modifier la marche, les collisions ou les vitesses. Les tests incluent désormais cette étape du client 26.3, dans les quatre directions et pour les six articulations. Le pack reste inchangé.

## Bestiaire articulé et rencontres — alpha.13

**Ground Zero → Bestiaire** permet de choisir chaque infecté, voir sa silhouette et modifier sa vie, ses dégâts, sa vitesse, son armure, sa taille, sa force contre les murs, son risque de morsure, sa fréquence et ses capacités spéciales. Les attributs changent sur les créatures déjà présentes, sans les soigner : la proportion de vie restante est conservée. Fréquence et plafond concernent les prochaines apparitions ; une fréquence de 0 désactive les apparitions du type.

Les sept mutants possèdent six parties articulées, tournent de face selon leur mouvement réel et adaptent la cadence de marche/course à leur déplacement. Les préparations d’attaque animent aussi le corps et les bras. Le rendu est borné à 16 créatures / 96 pièces près des joueurs et s’arrête sans spectateur. Le pack original complet reste inférieur à 170 Ko.

Les apparitions ont lieu **à plus de 100 et moins de 200 blocs**, avec un réglage par défaut de 110–190 blocs et vérification de la distance à tous les joueurs. Les infectés sont retirés si aucun joueur du monde n’est à 200 blocs ou moins, même si les apparitions sont désactivées. Trois groupes d’approche et 36 chunks maximum ; au plus un groupe intérieur en attente. Le plafond par défaut passe à 120, densité 2,5, trois renforts par passage. Les plafonds personnalisés sont préservés.

Certains **bâtiments abandonnés générés** peuvent abriter des infectés : 35 % des bâtiments sont éligibles, et 30 % des préparations cherchent un intérieur. Une visite laisse une marque persistante dans le chunk ; les pièces visitées et les bases revendiquées ne sont pas repeuplées. Les quartiers de `renovation-exclusions.json` sont aussi protégés. Les hôpitaux et les maisons construites par les joueurs sont exclus. Un fichier de protection invalide suspend les rencontres intérieures.

Les armes à feu donnent un recul de visée différent : faible sur la mitraillette, modéré au pistolet, fort au fusil à pompe et au lance-roquettes. S’accroupir le réduit. Rotation relative de caméra, sans déplacement ni retour forcé de la souris ; aucun recul ni usure supplémentaire quand le tir est refusé.

La migration de contenu 7 sauvegarde la configuration, applique l’anneau 110–190 / nettoyage 200 et relève les anciens plafonds standard. L’autotest Paper vérifie les six articulations, les quatre orientations, une vraie modification d’attributs à chaud, la conservation de vie et le verrouillage d’une pièce visitée. Le rendu visuel complet et le ressenti du recul doivent aussi être essayés avec un client 26.3.

## Arsenal en 3D et lance-roquettes — alpha.12

Le pistolet, le fusil à pompe et la mitraillette ont des modèles originaux reconnaissables en main, dans l’inventaire et au sol, avec leurs sons et leurs aperçus dans le panneau. Les armes déjà possédées reçoivent leur nouvelle apparence sans changer leur identité ni leur usure. Le pack facultatif commun passe à environ **150 Ko**, sans mod.

Le **lance-roquettes** devient l’arme de zone la plus puissante : projectile visible jusqu’à 96 blocs, impact arrêté par les obstacles, explosion de 6 blocs et 80 dégâts maximum avant distance, murs et protections. Les règles de PvP et du refuge s’appliquent ; attention aux tirs trop proches. Aucun bloc ni inventaire n’est détruit par l’explosion.

Chaque lanceur contient **10 tirs au total**, avec compteur et barre d’usure. Le dixième tir épuise l’arme. Ni recharge ni réparation, et le compteur persiste avec l’objet. Deux secondes entre les tirs, huit projectiles simultanés maximum ; le vol cesse si le tireur quitte la partie ou si la trajectoire sort des chunks chargés.

Fabrication à l’atelier mécanique : **8 pièces d’arme, 16 ferrailles, 6 charges propulsives, 4 ressorts, 4 rubans**. Également très rare dans le butin militaire et du laboratoire ; jamais dans un kit de départ. `/apocalypse recipes rocket_launcher` affiche la recette. Le catalogue administrateur Ground Zero permet aussi de le donner.

Panneau : **Ground Zero → Règles → L’arsenal de survie**, ou sélectionne un survivant pour choisir l’arme dans le catalogue. Sans le pack, les armes gardent un objet de repli nommé et leur fonctionnement ; le papier ordinaire conserve son apparence.

Le test `/apocalypse admin rocket-selftest`, réservé à un scénario vide, vérifie le vol réel, l’arrêt sur un mur, les dégâts, la conservation d’un coffre et de ses diamants, le plafond et le nettoyage des projectiles. Les tests d’objets vérifient aussi la limite de dix tirs après sérialisation et la conservation des anciennes armes. Le rendu en main reste à vérifier avec un client Minecraft.

## Bâtiments, sonar et bestiaire — alpha.11

**La ville devient praticable.** Entrées des maisons étroites dégagées, portes cohérentes et approches reliées à la rue. La rénovation vérifie les accès aux pièces, lits, escaliers et coffres avant modification. Les inventaires, blocs personnalisés et bases revendiquées sont conservés ; un bâtiment dont un ajout bloque le passage est ignoré. Aucune régénération du monde.

Pour préserver un quartier, crée `plugins/DuckCatApocalypse/renovation-exclusions.json` :

```json
{"version":1,"areas":[{"world":"ground_zero","label":"Mon quartier","minX":0,"maxX":95,"minZ":0,"maxZ":95}]}
```

Toutes les hauteurs sont couvertes, bornes incluses. Les bâtiments dont l’approche touche la zone sont entièrement exclus. Un fichier de protection invalide bloque la rénovation. Le panneau Maintenance affiche les exclusions. La rénovation avance si le monde est vide ou si tous les joueurs restent dans les quartiers préservés. Elle se met en pause dès qu’un joueur se trouve ailleurs.

**Repère le danger avant le contact.** Sonar de **100 blocs** dans Minecraft et sur le site. Accroupi + clic droit : zoom 50 / 100 blocs. Points bleus extérieurs : direction des joueurs éloignés. Actualisation native toutes les 500 ms, web toutes les 5 s ; aucun chargement de chunk par le radar.

**Ils arrivent de loin.** Chaque apparition automatique, animal infecté, renfort et membre d’une horde reste à **plus de 100 blocs horizontalement de tous les joueurs** et à moins de 200 blocs d’au moins un joueur. Préparation asynchrone, trois groupes d’approche et 36 chunks simultanés au maximum ; journal d’appartenance et nettoyage à l’arrêt. Les infectés approchent le lieu initial sans suivre les coordonnées d’un joueur caché. L’approche expire après deux minutes ; les plafonds incluent les réservations. Les hordes annoncent une préparation, pas un nombre déjà apparu.

| Infecté | Comment réagir |
|---|---|
| Cracheur d’acide | Charge verte, crachat à vue jusqu’à 20 blocs, flaque de 3 s. Frapper pendant la préparation l’interrompt. |
| Bondisseur | Élan avant un saut physique de plus de 3 blocs ; récupération de 7 s. |
| Rampant | Collision native de moins d’un bloc. Surveille les passages bas. |
| Traqueur décharné | Approche silencieuse, cri avant une courte accélération, puis récupération. |
| Boursouflé | Nuage toxique de 4 s sur 3 blocs autour du corps après élimination. Recule ! |
| Briseur | Force de siège doublée (14 → 28 par défaut), sans doubler ses dégâts directs. |

Acide et nuages respectent le refuge, les protections d’arrivée, les joueurs hors scénario, les modes créatif/spectateur et les murs. Aucun bloc, inventaire ou animal sain n’est abîmé. Plafonds de 12 projectiles et 12 zones actives ; dégâts toxiques limités à une application par joueur et par seconde.

**Pack original facultatif (~150 Ko).** Sept modèles et onze sons, sans mod. L’installation HTTPS prépare son URL portable. Le client peut refuser ; les créatures restent natives avec les mêmes statistiques. Si un joueur présent n’a pas chargé le pack, l’apparence native est conservée pour tous. Au plus 16 modèles de six pièces sont animés près des joueurs. Les autres packs du serveur restent chargés.

`/apocalypse admin horror-selftest` vérifie les vrais crachats, murs, sauts, rigs et locations de chunks dans un scénario vide. Résultat : `horror-selftest.json`. Le rendu final et l’acceptation du pack demandent aussi une vérification avec un client Minecraft.

La migration vers le contenu 5 sauvegarde `config.yml`, relève les distances, double une seule fois la force de siège et ajoute les profils rares. Les autres règles et poids existants restent conservés ; un poids 0 désactive un profil.

## Apparitions extérieures — alpha.10

Le filtre des apparitions extérieures reste strict : les intérieurs des bâtiments sont exclus, même après une modification de leur toit. Un toit ajouté par les joueurs bloque aussi les apparitions dessous et sur le toit dans le monde généré Ground Zero. Le même filtre s'applique aux apparitions ordinaires, animaux infectés du directeur, renforts des hurleurs et à chaque membre d'une horde. Les infectés déjà présents peuvent toujours entrer en marchant ou en enfonçant les obstacles.

Depuis alpha.13, `spawning.indoor-chance` active séparément les rencontres limitées aux bâtiments abandonnés ; le filtre extérieur continue de protéger les toits et les constructions personnelles. Contrôles bornés aux chunks chargés et aux essais existants, sans recherche de pièces ni balayage de bâtiments en arrière-plan.

## Coffres de loot utilisables — alpha.9

Les coffres et tonneaux de fouille acceptent les dépôts, glisser-déposer, Maj+clic et entonnoirs. Tu peux les casser : Minecraft fait tomber leur contenu. Un coffre non encore ouvert reçoit son butin une seule fois avant la casse. Les coffres doubles gardent des marqueurs séparés par moitié ; une extension posée par un joueur ne génère pas de loot.

Le réassort reste réservé aux inventaires entièrement vides après le délai actif et un rechargement de chunk : aucun dépôt n'est effacé. Le refuge et les protections contre les explosions restent applicables.

## Discrétion et recherche — alpha.8

Les infectés enquêtent sur les bruits : **marche (5 blocs), course (10), casse (12), pose (8), verre (16)**, coffres, combat, tirs et moteurs. Accroupi, les pas restent silencieux ; tourner la caméra ou maintenir Sprint sans avancer ne fait pas de bruit.

Après t'avoir vu, ils poursuivent et frappent les portes, vitres, barricades et murs construits qui les bloquent. Une fois caché, ils cherchent **ta dernière position vue ou entendue**, sans suivre tes déplacements à travers les murs. **30 secondes sans nouvelle vue ni bruit**, et la recherche ainsi que le siège cessent ; ils reprennent leur errance. Rester immobile en plein champ de vision ne suffit pas. Leurs propres coups et cris n'entretiennent pas indéfiniment la poursuite.

Les conteneurs, postes de fabrication, terrain naturel, refuge et bases dont tous les membres sont absents conservent leurs protections. La recherche ne charge pas de chunks et ne recalcule un chemin qu'une fois par seconde. Ground Zero → Règles → **Discrétion** permet de régler à chaud le délai et les portées. Le délai commun remplace les anciennes durées `memory-seconds` par archétype, conservées en lecture pour compatibilité.

## Mini-arbalète automatique — alpha.7

Fabrique-la à l'**atelier mécanique** : **1 arbalète Minecraft, 6 ferrailles, 2 ressorts et 4 planches**. Le catalogue `/apocalypse recipes auto_turret` affiche les ingrédients et le poste de fabrication. L'objet est également disponible dans le catalogue du panneau Ground Zero.

Pose-la sur un sol solide, hors du refuge, avec un bloc libre au-dessus. Son arbalète visible s'oriente vers l'infecté le plus proche à **8 blocs**, avec un véritable carreau toutes les **1,25 seconde**, sans munitions à recharger. Les murs bloquent les tirs. Les joueurs et les animaux sains ne sont jamais blessés par ses carreaux. Un tir inflige 4 points de dégâts avant armure.

**Clic droit à main vide** pour activer ou mettre en pause. Le propriétaire, son équipe ou un administrateur peuvent la casser pour récupérer l'objet ; la pause et le propriétaire sont conservés au rechargement du chunk et après relance. Quatre bases maximum par zone de 16 × 16 blocs. Ciblage borné par lots et aucun chargement de chunk pour surveiller les alentours.

Le test Paper asynchrone `/apocalypse admin turret-selftest` est réservé à un scénario vide : tirs réels, mur, cible hors portée, animal sain, pause et réindexation sans doublon. Il restaure son terrain et publie son résultat dans `turret-selftest.json`.

## Mise à jour survie — alpha.6

- **Carte → touche le sol → Me téléporter ici** : le compte web déplace son personnage après confirmation et vérification du sol. Chargement asynchrone ; inventaire, vie et progression conservés.
- **Un départ équipé** : chaque nouvelle vie reçoit une arme de mêlée aléatoire, 4–6 aliments, 2 bandages, 8 torches, une pioche et des provisions aléatoires. Une reconnexion ne redonne pas le kit. Les vies existantes reçoivent ce kit une seule fois.
- **Fouille plus généreuse** : 5–9 piles dans les maisons et boutiques, 7–12 au supermarché, 4–8 à la police et à l'entrepôt lorsque les anciens réglages par défaut étaient utilisés. Les anciens conteneurs de fouille reçoivent une seule fois 3 piles supplémentaires dans leurs emplacements libres. Les coffres des joueurs sont exclus.
- **Trois armes à feu sans balles** : pistolet au clic droit ; fusil à pompe à courte portée ; mitraillette en maintenant le clic droit. Une usure par tir, y compris une gerbe complète. Réparation à l'atelier avant la casse ; règles de PvP et du refuge respectées. Visuels Minecraft vanilla, aucun pack ni mod obligatoire.
- **Sonar personnel** : carte en seconde main (F), cercle de 100 blocs orienté selon le regard. Survivants en bleu, infectés et joueurs mordus proches en rouge ; joueurs lointains sur l'anneau extérieur bleu. `/apocalypse radar` récupère une carte perdue. Sur le panneau, sélectionne un survivant pour son sonar (positions toutes les 5 secondes).
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
| Fouille : butin par type de lieu (12 tables), tiré à la première ouverture et partagé, jamais retiré à la réouverture ; réapprovisionnement après 30 min de scénario actif, seulement vide et quand plus personne n'est dans les parages ; dépôts, entonnoirs et casse par les joueurs autorisés ; réassort sans effacer le stockage ; un coffre posé par un joueur ne devient jamais un conteneur de fouille | Implémenté | Autotest (tirage unique, remise à zéro) ; **non testé en jeu** |
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
2. Ouvrir un coffre d'une maison : son nom indique le lieu, le butin apparaît ; refermer et rouvrir : même contenu ; y déposer un objet puis le reprendre ; casser le coffre : son contenu tombe une seule fois.
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
- Les étages des immeubles et de l'hôpital se parcourent par des échelles ; les apparitions automatiques se font uniquement dehors.
- Le monde est généré à la volée : la première visite d'une zone coûte un peu plus au serveur qu'un monde plat.
