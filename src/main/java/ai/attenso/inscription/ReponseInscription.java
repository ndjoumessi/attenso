package ai.attenso.inscription;

/**
 * Ce que l'établissement voit juste après son inscription (EF-901).
 *
 * @param urlPaiement la page de paiement Stripe, vers laquelle la clinique est
 *        redirigée immédiatement. Tant qu'elle n'a pas abouti, la clinique
 *        existe mais n'est PAS active : ses adresses sont réservées, et rien
 *        ne tourne.
 * @param expediteurSmsPropose le nom que l'éditeur devra faire valider auprès
 *        des opérateurs. Annoncé tout de suite parce que c'est la seule étape
 *        qui n'est PAS autonome (§15, point 1) : la clinique doit savoir dès
 *        la première minute que ses SMS ne partiront pas avant cette
 *        validation, et que le reste fonctionne entre-temps.
 */
public record ReponseInscription(String slug, String urlComptoir, String urlEcran,
		String palier, String expediteurSmsPropose, String urlPaiement) {
}
