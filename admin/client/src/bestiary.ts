export const monsters: Record<
  string,
  { name: string; role: string; note: string; model?: boolean }
> = {
  walker: {
    name: 'Errant',
    role: 'La horde',
    note: 'Lent seul, dangereux quand les rues se remplissent. Il cherche l’origine des bruits.',
  },
  pursuer: {
    name: 'Poursuivant',
    role: 'La traque',
    note: 'Tenace et attentif. Brise sa ligne de vue puis reste silencieux pour lui échapper.',
  },
  runner: {
    name: 'Sprinteur',
    role: 'La vitesse',
    note: 'Une accélération brutale, puis un temps de récupération. Garde une porte de sortie.',
  },
  howler: {
    name: 'Hurleur',
    role: 'L’alarme',
    note: 'Son cri rameute les infectés. Un coup pendant sa préparation peut l’interrompre.',
  },
  spitter: {
    name: 'Cracheur d’acide',
    role: 'L’acide',
    model: true,
    note: 'Gorge déformée et poches corrosives. Esquive le crachat annoncé ; évite la flaque verte.',
  },
  jumper: {
    name: 'Bondisseur',
    role: 'Le bond',
    model: true,
    note: 'Longs bras griffus, silhouette ramassée. Son élan annonce un saut de plus de trois blocs.',
  },
  crawler: {
    name: 'Rampant',
    role: 'L’embuscade',
    model: true,
    note: 'Colonne apparente, langue et membres au ras du sol. Il passe sous les ouvertures d’un bloc.',
  },
  stalker: {
    name: 'Traqueur décharné',
    role: 'La surprise',
    model: true,
    note: 'Pâle, maigre et silencieux. Ses longues griffes deviennent redoutables pendant sa charge.',
  },
  bloated: {
    name: 'Boursouflé',
    role: 'Le poison',
    model: true,
    note: 'Un corps gonflé de poches toxiques. Même mort, son nuage vous oblige à reculer.',
  },
  breaker: {
    name: 'Briseur',
    role: 'La force',
    model: true,
    note: 'Un bras monstrueux, des excroissances osseuses. Très robuste, il enfonce les murs et les barricades.',
  },
  abomination: {
    name: 'Abomination',
    role: 'Le cauchemar',
    model: true,
    note: 'Mutation massive et asymétrique. Sa frappe au sol est annoncée : éloigne-toi avant l’impact.',
  },
  infected_wolf: {
    name: 'Loup infecté',
    role: 'Le prédateur',
    note: 'Rapide, mobile et capable de mordre. La limite globale de faune reste applicable.',
  },
  infected_livestock: {
    name: 'Bête infectée',
    role: 'La contamination',
    note: 'Une bête autrefois paisible devenue dangereuse. La limite globale de faune reste applicable.',
  },
};
