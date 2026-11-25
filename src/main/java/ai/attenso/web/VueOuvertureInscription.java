package ai.attenso.web;

import java.util.List;

/**
 * Les paliers, et si l'on peut souscrire en ligne (EF-902).
 *
 * <p>Le drapeau existe parce que la page doit le savoir AVANT toute saisie.
 * Sans réglage Stripe, l'inscription échouait en 503 une fois le formulaire
 * entièrement rempli — nom, adresse, mot de passe de douze caractères, formule,
 * conditions acceptées. L'information était disponible depuis le début
 * ({@code PasserellePaiement.disponible()}) ; elle n'était jamais dite.
 */
public record VueOuvertureInscription(boolean ouverte, List<VuePalier> paliers) {
}
