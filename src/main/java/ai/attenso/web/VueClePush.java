package ai.attenso.web;

/**
 * @param clePublique {@code null} quand le push n'est pas configuré. La page
 *                    masque alors son bouton plutôt que d'échouer à
 *                    l'abonnement : une fonction absente se tait.
 */
public record VueClePush(String clePublique) {
}
