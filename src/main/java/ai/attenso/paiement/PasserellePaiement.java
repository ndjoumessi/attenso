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

	/**
	 * Ouvre l'espace de gestion de l'abonnement et rend son adresse.
	 *
	 * <p>Une page hébergée, là encore, et pour une raison de fond : changer de
	 * formule, mettre à jour une carte, résilier et télécharger ses factures
	 * sont quatre écrans à écrire, à tester et à tenir à jour, pour des gestes
	 * que le prestataire sait déjà faire — avec l'authentification forte et la
	 * conformité qui vont avec. Les CGV promettent une résiliation « à tout
	 * moment » : c'est ce bouton qui rend la promesse vraie.
	 *
	 * @throws SouscriptionIndisponible si Stripe n'est pas configuré ou ne
	 *         répond pas.
	 */
	String ouvrirEspaceClient(String clientStripe);

	/** Vrai si la souscription en ligne peut aboutir. */
	boolean disponible();

	/**
	 * @param url      la page de paiement Stripe, vers laquelle rediriger.
	 * @param clientId l'identifiant du client Stripe, s'il est déjà connu.
	 */
	record Souscription(String url, String clientId) {
	}
}
