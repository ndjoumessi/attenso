package ai.attenso.paiement;

import ai.attenso.facturation.Palier;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La souscription passe par une page de paiement HÉBERGÉE PAR STRIPE.
 *
 * <p>C'est le choix central de ce fichier, et il n'est pas de confort : aucune
 * donnée de carte n'entre jamais dans Attenso, donc ce dépôt n'a rien à
 * protéger de ce côté, rien à chiffrer, rien à faire auditer. L'authentification
 * forte du payeur, les cartes européennes, le prélèvement SEPA et les relances
 * sont l'affaire de Stripe. Construire un formulaire de carte maison aurait
 * ajouté la pire des surfaces d'attaque à un produit qui, par ailleurs, ne
 * collecte qu'un numéro de mobile.
 */
@Component
public class PasserelleStripe implements PasserellePaiement {

	private static final Logger JOURNAL = LoggerFactory.getLogger(PasserelleStripe.class);

	private final ProprietesPaiement proprietes;

	private final String baseUrl;

	private final StripeClient stripe;

	public PasserelleStripe(ProprietesPaiement proprietes,
			@Value("${attenso.lien.base}") String baseUrl) {
		this.proprietes = proprietes;
		this.baseUrl = baseUrl;
		this.stripe = proprietes.complete() ? StripeClient.builder()
				.setApiKey(proprietes.cle()).build() : null;
		annoncerLEtat();
	}

	/**
	 * Au démarrage, dire ce qui marche et ce qui ne marchera pas.
	 *
	 * <p>Même règle que pour les clés de notification poussée : une absence
	 * totale est un choix d'exploitation et s'annonce en INFO, une
	 * configuration À MOITIÉ remplie est une faute et s'annonce en ERROR. Une
	 * ERROR à chaque démarrage normal apprendrait à ignorer les ERROR.
	 */
	private void annoncerLEtat() {
		if (proprietes.complete()) {
			JOURNAL.info("souscription en ligne active");
			return;
		}
		if (proprietes.manquants().size() == 2 + Palier.values().length) {
			JOURNAL.info("souscription en ligne inactive : aucun réglage Stripe. "
					+ "Les cliniques déjà installées fonctionnent normalement.");
			return;
		}
		JOURNAL.error("souscription en ligne INACTIVE : configuration Stripe "
				+ "incomplète, il manque {}", proprietes.manquants());
	}

	@Override
	public boolean disponible() {
		return stripe != null;
	}

	@Override
	public Souscription ouvrir(UUID tenantId, Palier palier, String email,
			String nomClinique) {
		if (stripe == null) {
			throw new SouscriptionIndisponible(
					"la souscription en ligne n'est pas configurée sur ce serveur");
		}
		try {
			Session session = stripe.checkout().sessions().create(parametres(tenantId,
					palier, email, nomClinique));
			return new Souscription(session.getUrl(), session.getCustomer());
		}
		catch (StripeException panne) {
			// Le message de Stripe peut nommer la clé ou la ressource ; il va
			// au journal, pas au navigateur.
			JOURNAL.error("ouverture de la souscription en échec pour « {} » : {}",
					nomClinique, panne.getMessage());
			throw new SouscriptionIndisponible(
					"le prestataire de paiement n'a pas répondu", panne);
		}
	}

	@Override
	public String ouvrirEspaceClient(String clientStripe) {
		if (stripe == null) {
			throw new SouscriptionIndisponible(
					"la gestion de l'abonnement n'est pas configurée sur ce serveur");
		}
		try {
			return stripe.billingPortal().sessions().create(
					com.stripe.param.billingportal.SessionCreateParams.builder()
							.setCustomer(clientStripe)
							.setLocale(com.stripe.param.billingportal.SessionCreateParams
									.Locale.FR)
							.setReturnUrl(baseUrl + "/accueil")
							.build())
					.getUrl();
		}
		catch (StripeException panne) {
			JOURNAL.error("ouverture de l'espace client en échec : {}", panne.getMessage());
			throw new SouscriptionIndisponible(
					"le prestataire de paiement n'a pas répondu", panne);
		}
	}

	private SessionCreateParams parametres(UUID tenantId, Palier palier, String email,
			String nomClinique) {
		return SessionCreateParams.builder()
				.setMode(SessionCreateParams.Mode.SUBSCRIPTION)
				.setLocale(SessionCreateParams.Locale.FR)
				.setCustomerEmail(email)
				// Le seul fil qui relie le paiement à la clinique : le webhook
				// de Stripe ne connaît rien de notre cloisonnement.
				.setClientReferenceId(tenantId.toString())
				.putMetadata("clinique", nomClinique)
				.putMetadata("palier", palier.name())
				.addLineItem(SessionCreateParams.LineItem.builder()
						.setPrice(proprietes.prixDe(palier))
						.setQuantity(1L)
						.build())
				// La clinique revient sur sa propre page de confirmation, qui
				// porte l'adresse de son comptoir et celle de son écran.
				.setSuccessUrl(baseUrl + "/inscription?paiement=ok&clinique=" + tenantId)
				.setCancelUrl(baseUrl + "/inscription?paiement=abandon")
				.build();
	}
}
