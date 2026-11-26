// Ce qu'on montre pendant qu'une liste charge.
//
// `Agenda` et `Carnets` n'affichaient rien : l'écran restait vide, puis tout
// apparaissait d'un coup. Pire qu'un texte d'attente, parce qu'on ne peut pas
// distinguer « ça travaille » de « c'est fini, il n'y a personne » — et ces
// deux vues font précisément cette distinction partout ailleurs.

/**
 * La hauteur d'une ligne de liste, et sa SEULE source.
 *
 * <p>Mesuré au navigateur : une ligne d'agenda chargée fait 70 px (heure 28 +
 * statut 16 + `p-3` des deux côtés 24 + bordure 2). Le squelette en faisait
 * 56, donc il réservait moins de place que ce qu'il annonçait et le contenu
 * SAUTAIT de 14 px par ligne au chargement — exactement ce qu'un squelette
 * existe pour éviter. Les deux la tiennent d'ici : une hauteur écrite à deux
 * endroits redivergera.
 */
export const LIGNE = 'min-h-[70px]';

/**
 * Des lignes grises au rythme de celles qui vont les remplacer.
 *
 * <p>`aria-hidden` : un lecteur d'écran n'a rien à faire de barres grises.
 * C'est `aria-busy` sur la liste qui porte l'information à la voix.
 *
 * <p>`hauteur` : les lignes de l'agenda sont toutes de la même taille, donc
 * le squelette peut réserver leur place exactement. Celles des carnets ne le
 * sont pas — adresse copiable et boutons QR selon l'animal — et aucune
 * hauteur n'y serait juste : là, le squelette ne fait que dire « ça travaille ».
 *
 * <p>Le battement passe par `animate-pulse`, donc par la règle globale de
 * `prefers-reduced-motion` qui ramène toute animation à 0,01 ms. Rien de
 * particulier à prévoir ici.
 */
export function Squelette({ lignes = 3, hauteur = LIGNE }) {
  return (
    <ul aria-hidden="true" className="mt-4 space-y-2">
      {Array.from({ length: lignes }, (_, i) => (
        <li key={i}
            className={`${hauteur} animate-pulse rounded-[var(--rayon)] border
                        border-bordure-douce bg-surface-douce`} />
      ))}
    </ul>
  );
}
