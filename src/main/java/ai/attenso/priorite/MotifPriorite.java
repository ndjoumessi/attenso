package ai.attenso.priorite;

import java.util.UUID;

/**
 * Un motif de passage prioritaire, paramétré par la clinique (EF-407).
 *
 * @param rang entre 1 et 9 : au-dessus de la file normale, jamais au niveau
 *             d'une urgence vitale. La contrainte est en base, pas ici — un
 *             réglage qui ferait doubler une urgence doit être impossible,
 *             pas seulement découragé.
 */
public record MotifPriorite(UUID id, String libelle, short rang, short ordre, boolean actif) {
}
