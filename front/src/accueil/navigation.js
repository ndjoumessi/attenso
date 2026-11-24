/**
 * Les vues du tableau du personnel, et la lecture du fragment qui les désigne.
 *
 * <p>Une seule entrée Vite, un seul paquet : `/accueil#mesure` est une VUE et
 * non une page serveur. C'est ce qui garde `pagePour` de la coquille hors
 * réseau à quatre entrées — elle ignore le fragment — et c'est ce qui laisse
 * l'état en mémoire survivre à la navigation : le flux SSE, la file de rejeu
 * et le bandeau « gestes en attente » ne se reconstruisent pas à chaque clic.
 *
 * <p>Ce module ne touche ni à `location` ni au DOM : il est la table, pas
 * l'écran, et c'est ce qui le rend lisible par `node --test` sans construire
 * le front.
 */
import {
  CalendarClock, ChartColumn, HeartPulse, ListOrdered, QrCode, Receipt,
  Settings, UsersRound,
} from 'lucide-react';

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
 * <p>`icone` est un composant de `lucide-react` — UNE seule famille, donc une
 * graisse, une taille optique et un style de trait communs. Les glyphes Unicode
 * de la première version venaient du croquis ASCII de §7.1, où ils tenaient
 * lieu de caractères de remplacement : mis côte à côte dans une vraie barre ils
 * venaient de blocs différents et faisaient bricolé, l'euro servant d'icône et
 * `⚇` ne disant rien de « comptes ».
 *
 * <p>Le LIBELLÉ reste ce qui porte le sens : l'icône ne paraît jamais seule.
 *
 * <p>« Urgence » et « Client sans mobile » ne sont pas ici : ce sont des
 * GESTES, donc des boutons de l'écran de la file, pas des destinations.
 */
export const VUES = [
  { cle: 'file', libelle: 'File du jour', groupe: null, icone: ListOrdered },
  { cle: 'rendez-vous', libelle: 'Rendez-vous', groupe: 'JOURNÉE', icone: CalendarClock },
  { cle: 'carnets', libelle: 'Carnets de santé', groupe: 'JOURNÉE', icone: HeartPulse },
  { cle: 'mesure', libelle: 'Mesure', groupe: 'PILOTAGE', icone: ChartColumn },
  { cle: 'exploitation', libelle: 'Exploitation', groupe: 'PILOTAGE', icone: Receipt },
  { cle: 'affiche', libelle: 'Affiche du comptoir', groupe: 'CLINIQUE', icone: QrCode },
  { cle: 'comptes', libelle: 'Comptes', groupe: 'CLINIQUE', icone: UsersRound },
  { cle: 'compte', libelle: 'Mon compte', groupe: 'COMPTE', icone: Settings },
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
