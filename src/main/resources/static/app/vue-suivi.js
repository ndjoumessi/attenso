// Ce que la page du client affiche, selon l'état de son ticket.
//
// Extrait de la page pour être testable : c'est ici qu'un défaut s'est
// logé — la carte annonçait « 2 personnes avant vous » à quelqu'un qu'on
// venait d'appeler.

import { minutes } from './formats.js';

export const CONSIGNES = {
  PREVENU: "C'est presque à vous — revenez à la clinique maintenant.",
  APPELE: "On vous appelle. Présentez-vous à l'accueil.",
  ABSENT: 'Vous avez été appelé. Signalez-vous à l\'accueil : vous repassez '
    + 'juste après le suivant.',
  EN_CONSULTATION: 'Vous êtes en consultation.',
  TERMINE: 'Consultation terminée. Bonne journée.',
  PARTI: 'Ce passage a été clos par la clinique.',
  ANNULE: 'Ce ticket a été annulé.',
};

/**
 * La grande carte : son étiquette, son chiffre, sa ligne du dessous.
 *
 * <p>Une file d'attente et un appel ne se racontent pas avec les mêmes mots.
 * Un ticket appelé garde sa position dans l'ordre d'arrivée — la position se
 * calcule sur l'ordre, pas sur qui est appelé — mais l'afficher alors serait
 * faux et coûteux : sur un téléphone, c'est le grand chiffre qu'on lit en
 * premier, et le client resterait assis dans sa voiture.
 *
 * <p>Dès qu'il est appelé, la carte montre SON numéro, celui que l'écran de la
 * salle affiche et que l'ASV annonce, et plus aucune estimation.
 */
export function carte(etat) {
  switch (etat.statut) {
    case 'APPELE':
      return { etiquette: "C'est à vous", valeur: etat.numero, dessous: '', appel: true };
    case 'EN_CONSULTATION':
      return { etiquette: 'Votre numéro', valeur: etat.numero, dessous: '', appel: true };
    case 'TERMINE':
    case 'PARTI':
    case 'ANNULE':
      return { etiquette: 'Votre numéro', valeur: etat.numero, dessous: '', appel: false };
    default:
      // EN_ATTENTE, PREVENU, ABSENT : la position a un sens, et pour un absent
      // elle en a même beaucoup — elle dit où il reprend sa place.
      return {
        etiquette: 'Personnes avant vous',
        valeur: Math.max(0, etat.position - 1),
        dessous: minutes(etat.attenteMinMinutes, etat.attenteMaxMinutes),
        appel: etat.position <= 1,
      };
  }
}

/** Le rappel du numéro n'a de sens que si la carte ne le montre pas déjà. */
export function rappelDuNumero(etat) {
  return carte(etat).etiquette === 'Personnes avant vous'
    ? `Votre numéro : ${etat.numero}` : '';
}

export function consigne(statut) {
  return CONSIGNES[statut] ?? '';
}

// Les deux gestes que la page client propose n'ont pas le même horizon, et
// aucun des deux ne vaut pour tous les statuts. Ils s'affichaient pourtant
// sous « Consultation terminée. Bonne journée. » — et l'appui n'était pas
// sans effet : `Ticket.signalerProche` n'exige aucun statut, donc un ticket
// clos recevait un « j'arrive » que l'accueil voyait sur son tableau.
//
// Deux prédicats et non un seul, parce qu'ils divergent réellement sur
// APPELE : y signaler son arrivée est précisément ce qui évite d'être marqué
// absent, tandis qu'y proposer une notification contredirait le titre de la
// page, qui dit déjà « C'est à vous ».
const ENCORE_UN_APPEL_A_VENIR = ['EN_ATTENTE', 'PREVENU', 'ABSENT'];

/** Tant que l'accueil peut encore tenir compte de « j'arrive ». */
export function peutSignalerSonArrivee(statut) {
  return ENCORE_UN_APPEL_A_VENIR.includes(statut) || statut === 'APPELE';
}

/** Une notification annonce l'appel à venir ; après l'appel, elle n'annonce rien. */
export function peutDemanderUneNotification(statut) {
  return ENCORE_UN_APPEL_A_VENIR.includes(statut);
}

/**
 * La grande carte a-t-elle quelque chose à montrer ?
 *
 * Non quand le lien n'a jamais rien rendu : la carte restait alors sur
 * « Personnes avant vous — / chargement… », un chargement qui ne finissait
 * jamais, pendant que l'erreur disait juste en dessous que le lien était mort.
 *
 * Oui dès qu'un état est connu, erreur ou pas : le message d'erreur réseau
 * promet « dernier état affiché », et cacher la carte en ferait un mensonge.
 */
export function doitMontrerLaCarte(etat, erreur) {
  return Boolean(etat) || !erreur;
}
