// Mises en forme partagées. Aucun import, aucun accès au DOM : ce module se
// charge aussi bien dans un navigateur que dans un runner de tests.

/** L'attente est annoncée en fourchette, jamais comme une promesse. */
export function minutes(min, max) {
  if (max <= 0) return "c'est votre tour";
  if (min === 0) return `moins de ${max} min`;
  return `${min} à ${max} min`;
}

/**
 * Depuis combien de temps cette personne attend (EF-402).
 *
 * <p>Arrondi à la minute, et jamais aux secondes : une durée qui défile
 * seconde par seconde sur un tableau transforme une salle d'attente en
 * chronomètre, pour l'ASV comme pour quiconque regarde par-dessus son épaule.
 * Au-delà d'une heure on passe en heures, parce qu'« 83 min » ne se lit pas.
 */
export function duree(depuis, maintenant = Date.now()) {
  const minutes = Math.max(0, Math.floor((maintenant - new Date(depuis)) / 60000));
  if (minutes < 1) return "à l'instant";
  if (minutes < 60) return `${minutes} min`;
  const heures = Math.floor(minutes / 60);
  const reste = minutes % 60;
  return reste === 0 ? `${heures} h` : `${heures} h ${String(reste).padStart(2, '0')}`;
}

/**
 * Un jour tel qu'on l'écrit : « mardi 24 novembre ».
 *
 * <p>Le serveur envoie une date de CALENDRIER — « 2026-11-24 », sans heure ni
 * fuseau — et le libellé ne doit donc dépendre du réglage d'aucun appareil.
 * L'instant est construit à midi UTC et mis en forme en UTC : le fuseau de la
 * machine ne rentre nulle part dans le calcul.
 *
 * <p>Le piège réel, et le seul : une date NUE. `new Date('2026-11-24')` est lue
 * comme minuit UTC par la spécification, alors que la mise en forme par défaut
 * se fait en heure locale — à l'ouest de Greenwich, le libellé recule donc d'un
 * jour. Mesuré à Pago Pago (UTC−11) : « lundi 23 novembre ». Trois des quatre
 * pages d'origine s'en gardaient en collant une heure à la date, ce qui suffit
 * puisque lecture et mise en forme se font alors dans le même fuseau.
 *
 * <p>Alors pourquoi tout en UTC plutôt que cette parade-là ? Parce qu'elle
 * repose sur une coïncidence — que les deux bouts restent dans le même
 * fuseau — et qu'elle tombe dès qu'un appelant passe un fuseau d'affichage
 * explicite. J'ai écrit cette version-là d'abord, et c'est un test qui me l'a
 * montrée : de Paris vers Kiritimati, treize heures d'écart faisaient passer au
 * LENDEMAIN. D'où aussi l'absence volontaire de paramètre de fuseau : une date
 * de calendrier n'a rien à régler, et ce paramètre ne pouvait que nuire.
 *
 * <p>Cette fonction est arrivée dans ce module parce que quatre pages portaient
 * chacune leur copie de la parade, dont une sous une forme différente, et
 * qu'aucune n'était testée. Une décision de fuseau recopiée quatre fois est une
 * décision qui dérivera.
 */
export function jourLong(jour) {
  return formater(jour, { weekday: 'long', day: 'numeric', month: 'long' });
}

/**
 * Le même, avec l'année : « 24 novembre 2026 ».
 *
 * <p>Pour le carnet de santé, où les vaccins s'étalent sur plusieurs années et
 * où « 24 novembre » seul ne dit pas si le rappel est fait ou en retard.
 */
export function jourAvecAnnee(jour) {
  return formater(jour, { day: 'numeric', month: 'long', year: 'numeric' });
}

function formater(jour, options) {
  return new Intl.DateTimeFormat('fr-FR', { ...options, timeZone: 'UTC' })
    .format(new Date(jour + 'T12:00:00Z'));
}

/** « 09:00:00 » → « 09:00 ». Les secondes d'un créneau n'intéressent personne. */
export function heureCourte(heure) {
  return heure.slice(0, 5);
}

/**
 * Regroupe des créneaux par jour, dans l'ordre reçu du serveur.
 *
 * <p>Une liste à plat de trois cents créneaux est illisible. L'ordre du serveur
 * est conservé — il trie déjà par jour puis par heure, et retrier ici ferait
 * deux sources de vérité pour l'ordre d'affichage.
 */
export function grouperParJour(creneaux) {
  const parJour = new Map();
  for (const creneau of creneaux) {
    if (!parJour.has(creneau.jour)) parJour.set(creneau.jour, []);
    parJour.get(creneau.jour).push(creneau);
  }
  return parJour;
}

/**
 * Un montant en centimes, tel qu'on l'écrit : « 69 € », « 0,15 € ».
 *
 * <p>Les centimes n'apparaissent que s'il y en a. Deux copies de cette
 * fonction existaient, et elles avaient déjà divergé : le tableau de
 * facturation écrivait « 69,00 € » là où la page d'inscription écrivait
 * « 69 € » pour le même palier. Un prix qui s'écrit de deux façons dans le
 * même produit donne l'impression de deux prix.
 *
 * <p>Les montants circulent en centimes ENTIERS depuis la base (voir
 * V11__facturation.sql) : la division par cent n'a lieu qu'ici, au moment de
 * l'affichage, et jamais dans un calcul.
 */
export function euros(centimes) {
  return (centimes / 100).toLocaleString('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: centimes % 100 === 0 ? 0 : 2,
  });
}
