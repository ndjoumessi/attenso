package ai.attenso.web;

import java.util.UUID;

/** Un motif de priorité tel que l'accueil le voit dans sa liste (EF-407). */
public record VueMotif(UUID id, String libelle, short rang) {
}
