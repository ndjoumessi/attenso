package ai.attenso.web;

import ai.attenso.paiement.ProprietesPaiement;
import ai.attenso.paiement.ServicePaiement;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le retour de Stripe.
 *
 * <p>Cette route est PUBLIQUE, et c'est la signature qui l'authentifie — rien
 * d'autre. Sans vérification, n'importe qui pourrait poster un
 * « checkout.session.completed » et s'offrir un abonnement gratuit ; c'est
 * pourquoi le corps est pris en texte BRUT et non désérialisé par Spring : la
 * signature porte sur les octets exacts, et un objet reconstruit puis
 * ré-encodé ne les reproduit pas.
 */
@RestController
public class ControleurPaiement {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ControleurPaiement.class);

	private final ServicePaiement paiements;

	private final ProprietesPaiement proprietes;

	public ControleurPaiement(ServicePaiement paiements, ProprietesPaiement proprietes) {
		this.paiements = paiements;
		this.proprietes = proprietes;
	}

	@PostMapping("/api/paiement/webhook")
	public ResponseEntity<String> webhook(@RequestBody String corps,
			@RequestHeader(value = "Stripe-Signature", required = false) String signature) {
		if (!proprietes.complete()) {
			// Un webhook alors que rien n'est configuré : soit la
			// configuration est partielle, soit quelqu'un tâte la porte.
			JOURNAL.warn("webhook de paiement reçu alors que Stripe n'est pas configuré");
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("inactif");
		}
		if (signature == null || signature.isBlank()) {
			return refus("webhook sans signature");
		}
		Event evenement;
		try {
			evenement = Webhook.constructEvent(corps, signature, proprietes.secretWebhook());
		}
		catch (SignatureVerificationException fausse) {
			return refus("signature de webhook invalide");
		}
		paiements.traiter(evenement);
		// 200 même quand l'événement ne nous concerne pas : une erreur ferait
		// rejouer Stripe indéfiniment pour un événement qu'on ne traitera
		// jamais, et finirait par faire désactiver le point de terminaison.
		return ResponseEntity.ok("reçu");
	}

	/**
	 * Un refus ne dit PAS ce qui cloche. Distinguer « pas de signature » de
	 * « signature fausse » renseignerait qui essaie ; le journal, lui, le
	 * distingue.
	 */
	private ResponseEntity<String> refus(String motif) {
		JOURNAL.warn("{} — rejeté", motif);
		return ResponseEntity.badRequest().body("refusé");
	}
}
