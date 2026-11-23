// Ce que le tableau de l'accueil affiche.
//
// Extrait de la page pour être testable, et parce que ce module porte un
// MIROIR de règles du serveur : la liste des états ouverts et le calcul des
// rangs. Un miroir qui dérive ment à l'ASV pendant une panne, au moment précis
// où elle n'a que lui.

/**
 * Miroir de {@code Statut.OUVERTS}. Sert à recalculer les rangs quand le
 * réseau est tombé : afficher le rang figé du dernier tableau reçu mentirait
 * dès le premier geste de l'ASV. À garder aligné sur ServiceFile.
 */
export const OUVERTS = new Set(['EN_ATTENTE', 'PREVENU', 'APPELE', 'ABSENT']);

/** L'état visé par chaque action, pour l'affichage optimiste. */
export const CIBLES = {
  appeler: 'APPELE',
  present: 'EN_CONSULTATION',
  termine: 'TERMINE',
  parti: 'PARTI',
  annule: 'ANNULE',
};

export const CLASSES = {
  // Chaque état du serveur a son entrée, même quand elle est vide : la carte
  // doit se lire comme la liste complète, pour qu'un état ajouté côté serveur
  // saute aux yeux plutôt que de tomber dans un défaut implicite.
  EN_ATTENTE: '',
  EN_CONSULTATION: '',
  APPELE: 'ligne--appele',
  PREVENU: 'ligne--prevenu',
  ABSENT: 'ligne--absent',
  TERMINE: 'ligne--clos',
  PARTI: 'ligne--clos',
  ANNULE: 'ligne--clos',
};

export const LIBELLES = {
  EN_ATTENTE: 'en attente',
  PREVENU: 'prévenu',
  APPELE: 'appelé',
  ABSENT: 'absent, revient',
  EN_CONSULTATION: 'en consultation',
  TERMINE: 'terminé',
  PARTI: 'parti sans être vu',
  ANNULE: 'annulé',
};

/**
 * Les actions proposées sont exactement les transitions permises par la
 * machine à états du serveur : un bouton qui apparaît est un bouton qui
 * marche.
 *
 * <p>« Parti » garde sa propre forme partout où il apparaît, et n'est jamais
 * le bouton principal : le confondre avec « absent » fausserait le compteur de
 * départs, qui est le seul chiffre justifiant l'abonnement.
 */
export const ACTIONS = {
  EN_ATTENTE: [['appeler', "J'appelle", 'b-principal'], ['parti', 'Parti', 'b-parti']],
  PREVENU: [['appeler', "J'appelle", 'b-principal'], ['parti', 'Parti', 'b-parti']],
  ABSENT: [['present', 'Présent', 'b-principal'], ['appeler', "J'appelle", ''],
           ['parti', 'Parti', 'b-parti']],
  APPELE: [['present', 'Présent', 'b-principal'], ['parti', 'Parti', 'b-parti']],
  EN_CONSULTATION: [['termine', 'Terminé', 'b-principal']],
};

export function actionsPour(statut) {
  return ACTIONS[statut] ?? [];
}

/**
 * Applique les gestes pas encore acquittés au tableau reçu du serveur, puis
 * renumérote les rangs avec la règle du serveur : l'ordre est conservé, et
 * seuls les états ouverts consomment un rang.
 *
 * <p>Les compteurs sont recalculés sur le résultat, pas repris du serveur :
 * sinon « en salle » et « partis » contrediraient les lignes juste en dessous.
 *
 * @param commandes dans l'ordre où l'ASV les a faites — la dernière gagne si
 *        deux visent le même ticket.
 */
export function avecEnAttente(vue, commandes) {
  const cibles = new Map();
  for (const commande of commandes) {
    if (commande.ticketId && commande.cible) {
      cibles.set(commande.ticketId, commande.cible);
    }
  }

  const lignes = vue.lignes.map((ligne) => cibles.has(ligne.id)
    ? { ...ligne, statut: cibles.get(ligne.id), enAttenteEnvoi: true }
    : { ...ligne, enAttenteEnvoi: false });

  let rang = 0;
  for (const ligne of lignes) {
    ligne.position = OUVERTS.has(ligne.statut) ? ++rang : 0;
  }

  return {
    ...vue,
    lignes,
    enFile: lignes.filter((l) => OUVERTS.has(l.statut)).length,
    departs: lignes.filter((l) => l.statut === 'PARTI').length,
  };
}

/** Signature de l'affichage : sert à ne redessiner que si quelque chose a bougé. */
export function signature(lignes) {
  return JSON.stringify(lignes.map((l) =>
    [l.id, l.statut, l.position, l.enAttenteEnvoi, l.joignable, l.priorite]));
}
