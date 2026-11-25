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
  Settings, TriangleAlert, UserRoundPlus, UsersRound,
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
 * GESTES, donc des boutons de l'écran de la file, pas des destinations. Voir
 * `GESTES` plus bas, qui est l'autre moitié de cette décision.
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
 * Les deux GESTES que porte l'écran de la file.
 *
 * <p>Ce ne sont pas des endroits où l'on va, ce sont des choses que l'on fait,
 * et c'est pourquoi ils ne figurent pas dans `VUES`. Ils vivaient en SECTIONS
 * sous la liste : sur une tablette, insérer une urgence demandait de faire
 * défiler la file entière, c'est-à-dire de perdre de vue ce qu'on est en train
 * de faire. Ils sont maintenant deux boutons en tête de la vue file, chacun
 * ouvrant son formulaire dans une boîte de dialogue.
 *
 * <p>La table vit ici, à côté de `VUES`, et non dans le composant : « qu'est-ce
 * qu'une destination, qu'est-ce qu'un geste » est UNE décision, elle se lit
 * d'un seul endroit, et elle s'éprouve sans rendre de DOM.
 *
 * <p>Les deux gestes sont indisponibles hors réseau, et ce n'est pas une
 * limite de la file de rejeu : le NUMÉRO du ticket est attribué par le
 * serveur, et deux tablettes qui en inventeraient un pendant une panne
 * donneraient le même à deux animaux. `aideHorsReseau` est donc une exigence
 * et non un ornement — un bouton grisé sans explication passe pour une panne.
 */
export const GESTES = [
  {
    cle: 'urgence',
    libelle: 'Urgence',
    // Même famille que les icônes de la barre, pour la même raison : une
    // graisse, une taille optique et un style de trait communs.
    icone: TriangleAlert,
    // EF-406. Le libellé du bouton est court parce qu'il est en tête d'écran ;
    // la phrase complète reste le titre de la boîte de dialogue.
    titre: 'Insérer une urgence',
    route: '/api/tickets/urgence',
    aide: 'Passe devant tout le monde. Les personnes déjà prévenues reçoivent '
      + 'automatiquement un message leur annonçant le délai supplémentaire.',
    valider: 'Insérer une urgence',
    avecMotif: false,
    aideHorsReseau: 'Indisponible hors réseau : le numéro du ticket est attribué '
      + 'par le serveur. Appelez le client à la voix, et saisissez-le au retour '
      + 'du réseau.',
    messageReseau: "Serveur injoignable : l'urgence ne peut pas être "
      + 'enregistrée hors réseau.',
  },
  {
    cle: 'client',
    libelle: 'Client sans mobile',
    icone: UserRoundPlus,
    // EF-104. « Sans mobile » et non « sans smartphone » : c'est le mobile qui
    // reçoit le message, et c'est le mot que l'ASV emploie au comptoir.
    titre: 'Ajouter un client sans mobile',
    route: '/api/tickets/client',
    aide: "Pour quelqu'un qui n'a pas pu scanner le QR code. Le mobile est "
      + 'facultatif : sans numéro, la personne reste dans la file mais devra '
      + 'être appelée à la voix.',
    valider: 'Ajouter à la file',
    avecMotif: true,
    aideHorsReseau: 'Indisponible hors réseau : le numéro du ticket est attribué '
      + 'par le serveur.',
    messageReseau: 'Serveur injoignable : le ticket ne peut pas être créé '
      + 'hors réseau.',
  },
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
