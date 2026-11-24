package ai.attenso.web;

import java.util.UUID;

/**
 * Création d'un ticket par l'accueil (EF-108), éventuellement prioritaire
 * (EF-407).
 *
 * @param telephone {@code null} pour quelqu'un sans téléphone du tout.
 * @param motifId   identifiant d'un motif de priorité actif de la clinique,
 *                  ou {@code null} pour la file normale. L'identifiant et non
 *                  le libellé : accepter un libellé libre laisserait l'accueil
 *                  inventer des priorités hors de la liste que la clinique a
 *                  décidée, et le rang irait avec.
 */
public record DemandeClient(String telephone, UUID motifId) {
}
