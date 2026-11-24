package ai.attenso.paiement;

import ai.attenso.tenant.ContexteTenant;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applique à la base ce que Stripe raconte.
 *
 * <p><strong>Un impayé ne coupe rien.</strong> C'est la décision la plus
 * importante de ce fichier et elle n'est pas technique : Attenso fait tourner
 * une salle d'attente. Couper la file un mardi matin parce qu'une carte a
 * expiré laisserait de vraies personnes debout devant une clinique qui ne
 * peut plus les appeler, et punirait les clients pour le moyen de paiement du
 * vétérinaire. L'état IMPAYE est donc visible — bandeau sur le tableau, et
 * c'est tout. Les relances sont l'affaire de Stripe, puis la nôtre, par
 * courrier et par téléphone, comme n'importe quel impayé entre professionnels.
 *
 * <p>Ce qui ferme réellement le service, c'est une RÉSILIATION, à sa date, et
 * cette date est celle de la fin de période payée.
 */
@Service
public class ServicePaiement {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServicePaiement.class);

	private final PaiementDao dao;

	private final JdbcTemplate jdbc;

	private final Clock horloge;

	public ServicePaiement(PaiementDao dao, JdbcTemplate jdbc, Clock horloge) {
		this.dao = dao;
		this.jdbc = jdbc;
		this.horloge = horloge;
	}

	/**
	 * @return vrai si l'événement a été appliqué, faux s'il était déjà connu
	 *         ou s'il ne nous concerne pas. Dans les deux cas Stripe reçoit un
	 *         accusé : lui rendre une erreur le ferait rejouer indéfiniment un
	 *         événement que nous ne traiterons jamais.
	 */
	@Transactional
	public boolean traiter(Event evenement) {
		// La réservation de l'identifiant et le traitement sont dans LA MÊME
		// transaction : si le traitement échoue, la réservation disparaît avec
		// lui et le rejeu de Stripe pourra la reprendre. L'inverse — accuser
		// puis traiter — perdrait l'événement sur la première panne.
		if (!dao.enregistrerSiNouveau(evenement.getId(), evenement.getType())) {
			JOURNAL.debug("événement {} déjà traité", evenement.getId());
			return false;
		}
		return switch (evenement.getType()) {
			case "checkout.session.completed" -> paiementConfirme(evenement);
			case "invoice.paid", "invoice.payment_succeeded" -> changerEtat(evenement, "ACTIF");
			case "invoice.payment_failed" -> changerEtat(evenement, "IMPAYE");
			case "customer.subscription.deleted" -> resiliation(evenement);
			default -> {
				JOURNAL.debug("événement {} ignoré", evenement.getType());
				yield false;
			}
		};
	}

	private boolean paiementConfirme(Event evenement) {
		Optional<StripeObject> objet = evenement.getDataObjectDeserializer().getObject();
		if (objet.isEmpty() || !(objet.get() instanceof Session session)) {
			return illisible(evenement);
		}
		UUID tenantId = lireTenant(session.getClientReferenceId());
		if (tenantId == null) {
			JOURNAL.error("paiement confirmé sans clinique identifiable : session {}",
					session.getId());
			return false;
		}
		return sousClinique(tenantId, () -> {
			boolean fait = dao.activer(tenantId, session.getCustomer(),
					session.getSubscription());
			JOURNAL.info("abonnement {} pour la clinique {}",
					fait ? "activé" : "déjà actif", tenantId);
			return fait;
		});
	}

	private boolean changerEtat(Event evenement, String statut) {
		Optional<StripeObject> objet = evenement.getDataObjectDeserializer().getObject();
		if (objet.isEmpty() || !(objet.get() instanceof Invoice facture)) {
			return illisible(evenement);
		}
		String abonnementStripe = abonnementDe(facture);
		if (abonnementStripe == null) {
			// Une facture hors abonnement : rien à faire, et ce n'est pas une
			// anomalie.
			return false;
		}
		UUID tenantId = dao.tenantDe(abonnementStripe).orElse(null);
		if (tenantId == null) {
			JOURNAL.warn("abonnement Stripe {} inconnu de cette base", abonnementStripe);
			return false;
		}
		return sousClinique(tenantId, () -> {
			boolean fait = dao.marquer(tenantId, statut);
			if (fait) {
				JOURNAL.info("clinique {} passe en {}", tenantId, statut);
			}
			return fait;
		});
	}

	private boolean resiliation(Event evenement) {
		Optional<StripeObject> objet = evenement.getDataObjectDeserializer().getObject();
		if (objet.isEmpty() || !(objet.get() instanceof Subscription abonnement)) {
			return illisible(evenement);
		}
		UUID tenantId = dao.tenantDe(abonnement.getId()).orElse(null);
		if (tenantId == null) {
			JOURNAL.warn("résiliation d'un abonnement inconnu : {}", abonnement.getId());
			return false;
		}
		return sousClinique(tenantId, () -> {
			boolean fait = dao.resilier(tenantId, LocalDate.now(horloge));
			JOURNAL.info("clinique {} résiliée", tenantId);
			return fait;
		});
	}

	private static String abonnementDe(Invoice facture) {
		Invoice.Parent parent = facture.getParent();
		if (parent == null || parent.getSubscriptionDetails() == null) {
			return null;
		}
		return parent.getSubscriptionDetails().getSubscription();
	}

	private static UUID lireTenant(String reference) {
		if (reference == null) {
			return null;
		}
		try {
			return UUID.fromString(reference);
		}
		catch (IllegalArgumentException pasUnUuid) {
			return null;
		}
	}

	/**
	 * Agit sous le cloisonnement d'une clinique.
	 *
	 * <p>Lier le ThreadLocal ne suffit PAS : la connexion est obtenue à
	 * l'ouverture de la transaction, donc avant que le webhook sache de quelle
	 * clinique il parle. Le réglage est donc posé sur CETTE connexion, en
	 * portée transactionnelle, exactement comme à l'inscription.
	 */
	private boolean sousClinique(UUID tenantId, java.util.function.Supplier<Boolean> action) {
		jdbc.queryForObject("SELECT set_config('attenso.tenant_id', ?, true)", String.class,
				tenantId.toString());
		return ContexteTenant.avec(tenantId, action);
	}

	/**
	 * Une charge que le SDK ne sait pas relire, presque toujours un écart de
	 * version d'API entre le compte Stripe et cette bibliothèque. On le dit
	 * fort : silencieux, le symptôme serait « les abonnements ne s'activent
	 * plus », découvert par un client qui a payé.
	 */
	private boolean illisible(Event evenement) {
		JOURNAL.error("charge illisible pour l'événement {} ({}) — vérifier la version "
				+ "d'API du compte Stripe", evenement.getId(), evenement.getType());
		return false;
	}
}
