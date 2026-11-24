package ai.attenso.web;

/**
 * Qui est connecté sur cette tablette.
 *
 * <p>Rien de secret : c'est écrit à l'écran de la personne qui vient de taper
 * ces identifiants. Mais rien ne le disait jusqu'ici, et sur un poste partagé
 * « qui suis-je » est la question qui précède « comment je m'en vais ».
 */
public record VueCompte(String email, String role) {
}
