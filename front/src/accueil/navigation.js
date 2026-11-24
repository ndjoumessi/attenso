/**
 * Les vues du tableau du personnel, et la lecture du fragment qui les désigne.
 *
 * <p>Une seule entrée Vite, un seul paquet : `/accueil#mesure` est une VUE et
 * non une page serveur. C'est ce qui garde `pagePour` de la coquille hors
 * réseau à quatre entrées — elle ignore le fragment — et c'est ce qui laisse
 * l'état en mémoire survivre à la navigation : le flux SSE, la file de rejeu
 * et le bandeau « gestes en attente » ne se reconstruisent pas à chaque clic.
 *
 * <p>Ce module est volontairement SANS DÉPENDANCE : ni React, ni icônes, ni
 * `location`. Il est la table, pas l'écran, et c'est ce qui le rend lisible
 * par `node --test` sans construire le front.
 */

/** La vue servie quand le fragment ne nomme rien de connu. */
const DEFAUT = 'file';

/**
 * L'ordre d'affichage des groupes nommés de la barre.
 *
 * <p>La file n'y figure pas : elle est ÉPINGLÉE au-dessus des groupes, parce
 * qu'elle est l'écran par défaut et non une destination parmi d'autres.
 */
export const GROUPES = ['JOURNÉE', 'PILOTAGE', 'CLINIQUE', 'COMPTE'];

/**
 * Les huit vues, dans l'ordre de la barre.
 *
 * <p>`icone` est un glyphe et non un composant : garder cette table sans
 * dépendance vaut plus qu'un jeu d'icônes homogène, et le LIBELLÉ est ce qui
 * porte le sens de toute façon — jamais l'icône seule.
 *
 * <p>« Urgence » et « Client sans mobile » ne sont pas ici : ce sont des
 * GESTES, donc des boutons de l'écran de la file, pas des destinations.
 */
export const VUES = [
  { cle: 'file', libelle: 'File du jour', groupe: null, icone: '▦' },
  { cle: 'rendez-vous', libelle: 'Rendez-vous', groupe: 'JOURNÉE', icone: '◷' },
  { cle: 'carnets', libelle: 'Carnets de santé', groupe: 'JOURNÉE', icone: '♡' },
  { cle: 'mesure', libelle: 'Mesure', groupe: 'PILOTAGE', icone: '◳' },
  { cle: 'exploitation', libelle: 'Exploitation', groupe: 'PILOTAGE', icone: '€' },
  { cle: 'affiche', libelle: 'Affiche du comptoir', groupe: 'CLINIQUE', icone: '⌸' },
  { cle: 'comptes', libelle: 'Comptes', groupe: 'CLINIQUE', icone: '⚇' },
  { cle: 'compte', libelle: 'Mon compte', groupe: 'COMPTE', icone: '⚙' },
];

/**
 * La clé de vue que désigne un fragment d'URL.
 *
 * <p>Tout ce qui n'est pas une clé déclarée retombe sur la file, y compris
 * `null`, la chaîne vide et le `#` nu : un favori posé sur une vue depuis
 * renommée doit rendre la file, pas un écran blanc.
 *
 * @param {string | null | undefined} fragment par exemple `'#mesure'`
 * @returns {string} une clé de `VUES`
 */
export function vuePour(fragment) {
  const cle = String(fragment ?? '').replace(/^#/, '');
  return VUES.some((v) => v.cle === cle) ? cle : DEFAUT;
}

/** Le libellé d'une vue, pour l'en-tête de l'écran. */
export function libellePour(cle) {
  return VUES.find((v) => v.cle === cle)?.libelle ?? '';
}
