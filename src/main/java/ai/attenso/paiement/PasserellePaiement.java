package ai.attenso.paiement;

import ai.attenso.facturation.Palier;
import java.util.UUID;

/**
 * Ce que le reste du produit a le droit de demander à Stripe.
 *
 * <p>Une interface, et pas un appel direct au SDK, pour deux raisons tenues
 * ailleurs dans ce dépôt : les tests tournent sans réseau ni compte, et une
 * clinique dont la configuration de paiement est incomplète doit recevoir un
 * refus clair plutôt qu'une pile d'exception.
 */
public interface PasserellePaiement {

	/**
	 * Ouvre une page de paiement hébergée et rend son adresse.
	 *
	 * @param tenantId porté jusqu'au webhook, qui n'a aucun autre moyen de
	 *        savoir quelle clinique vient de payer.
	 * @throws SouscriptionIndisponible si Stripe n'est pas configuré ou ne
	 *         répond pas. L'inscription échoue alors en entier.
	 */
	Souscription ouvrir(UUID tenantId, Palier palier, String email, String nomClinique);

	/** Vrai si la souscription en ligne peut aboutir. */
	boolean disponible();

	/**
	 * @param url      la page de paiement Stripe, vers laquelle rediriger.
	 * @param clientId l'identifiant du client Stripe, s'il est déjà connu.
	 */
	record Souscription(String url, String clientId) {
	}
}
