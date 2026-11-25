// Ce que la page du comptoir conclut d'un échec de lecture.
//
// Le défaut : `.catch(() => {})` avalait tout, et la page retombait sur
// « Cette clinique ». Pour un slug inconnu ou une clinique désactivée —
// `findBySlugAndActifTrue` rend un 404 —, le formulaire restait actif, le
// client donnait son numéro de mobile, et c'est l'envoi qui échouait.

export const ADRESSE_INACTIVE = "Cette adresse ne mène à aucune clinique "
  + "active. Adressez-vous à l'accueil : il peut vous inscrire directement.";

/**
 * Faut-il barrer le formulaire ?
 *
 * <p>Seul un 404 est définitif. Une panne passagère — hors ligne, 5xx, débit
 * limité — doit laisser le comptoir ouvert : la mention d'information reste
 * vraie mais générique, et la prise de ticket réessaiera. Barrer sur
 * n'importe quel échec fermerait le comptoir à chaque coupure de wifi.
 */
export function adresseInactive(echec) {
  return echec?.statut === 404;
}
