package ai.attenso.notification;

import ai.attenso.config.ProprietesOctopush;
import ai.attenso.config.ProprietesSms;
import ai.attenso.tenant.NomExpediteur;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envoi par Octopush.
 *
 * <p>Trois détails de ce fichier viennent de la documentation de l'API et
 * n'auraient pas pu être devinés.
 *
 * <p><strong>1. {@code request_id}.</strong> L'agrégateur refuse une requête
 * portant un identifiant déjà vu pendant 24 h. On lui passe l'identifiant de
 * la notification : sans ça, une coupure réseau survenant <em>après</em>
 * qu'Octopush a accepté le message ferait repartir notre réessai, et le SMS
 * serait envoyé — et facturé — deux fois. La boucle de réessai créait donc
 * elle-même une fuite d'argent, que l'API sait fermer.
 *
 * <p><strong>2. L'expéditeur est validé ici.</strong> La documentation annonce
 * que l'API, devant un paramètre mal formé, le « corrige » plutôt que de le
 * refuser — en supprimant les caractères interdits. Un SMS partirait donc sous
 * un expéditeur que personne n'a enregistré auprès des opérateurs, ce qui est
 * précisément ce qu'il ne faut pas quand cet expéditeur est l'identité de la
 * clinique.
 *
 * <p><strong>3. {@code purpose = alert}.</strong> Transactionnel, donc ni
 * mention STOP ni restriction horaire. Déclarer « wholesale » ferait refuser
 * nos messages faute de mention STOP (code 121), et interdirait l'envoi le
 * dimanche — un jour où les cliniques de garde travaillent.
 */
@Component
@ConditionalOnProperty(name = "attenso.sms.expediteur", havingValue = "octopush")
public class ExpediteurSmsOctopush implements ExpediteurSms {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ExpediteurSmsOctopush.class);

	private final RestClient client;

	private final ProprietesOctopush octopush;

	private final ProprietesSms sms;

	public ExpediteurSmsOctopush(@Qualifier("clientOctopushSms") RestClient client,
			ProprietesOctopush octopush, ProprietesSms sms) {
		this.client = client;
		this.octopush = octopush;
		this.sms = sms;
	}

	@Override
	public ResultatEnvoi envoyer(MessageSms message) {
		if (!NomExpediteur.valide(message.expediteur())) {
			return ResultatEnvoi.echec("expéditeur « %s » non conforme : 3 à 11 caractères "
					.formatted(message.expediteur())
					+ "alphanumériques sans accent, et enregistré auprès des opérateurs");
		}

		Map<String, Object> corps = Map.of(
				"recipients", List.of(Map.of("phone_number", message.destinataire())),
				"text", message.corps(),
				"type", octopush.type(),
				"purpose", octopush.purpose(),
				"sender", message.expediteur(),
				// Clé d'idempotence, valable 24 h côté Octopush.
				"request_id", message.reference().toString(),
				"simulation_mode", octopush.simulation());

		try {
			ReponseOctopush reponse = client.post()
					.uri("/v1/public/sms-campaign/send")
					.contentType(MediaType.APPLICATION_JSON)
					.body(corps)
					.retrieve()
					.body(ReponseOctopush.class);

			if (reponse == null || reponse.smsTicket() == null) {
				return ResultatEnvoi.echec("réponse Octopush sans identifiant de campagne");
			}
			return ResultatEnvoi.reussi(reponse.smsTicket(), estimation(message.corps()),
					reponse.totalCost());
		}
		catch (RestClientResponseException refus) {
			// L'API répond {"code": 121, "message": "..."} : on garde SON
			// message, pas un code HTTP nu. C'est lui qui dit « expéditeur non
			// autorisé » ou « crédit épuisé », et c'est ce qu'il faut lire dans
			// la table des notifications six semaines plus tard.
			JOURNAL.warn("Octopush refuse la notification {} : {} {}", message.reference(),
					refus.getStatusCode(), refus.getResponseBodyAsString());
			return ResultatEnvoi.echec(motifDuRefus(refus));
		}
		catch (RestClientException panne) {
			JOURNAL.warn("envoi Octopush injoignable pour la notification {} : {}",
					message.reference(), panne.getMessage());
			return ResultatEnvoi.echec(resumer(panne.getMessage(),
					panne.getClass().getSimpleName()));
		}
	}

	/**
	 * Notre propre estimation, conservée à côté du coût renvoyé par
	 * l'agrégateur : c'est elle que le modèle économique utilise, et l'unité du
	 * champ renvoyé n'est pas documentée.
	 */
	private BigDecimal estimation(String corps) {
		return sms.coutCentimesParSegment()
				.multiply(BigDecimal.valueOf(CompteurSegments.segments(corps)));
	}

	private String motifDuRefus(RestClientResponseException refus) {
		String corps = refus.getResponseBodyAsString();
		try {
			ErreurOctopush erreur = new tools.jackson.databind.ObjectMapper()
					.readValue(corps, ErreurOctopush.class);
			if (erreur != null && erreur.message() != null) {
				return "Octopush %s : %s".formatted(
						erreur.code() == null ? "" : erreur.code(), erreur.message()).trim();
			}
		}
		catch (RuntimeException illisible) {
			// Corps inattendu : on garde le texte brut, tronqué.
		}
		return resumer(corps, refus.getStatusText());
	}

	private String resumer(String texte, String repli) {
		if (texte == null || texte.isBlank()) {
			return repli;
		}
		return texte.substring(0, Math.min(texte.length(), 300));
	}

	/** Réponse 201 de l'API. Les clés sont en snake_case côté Octopush. */
	record ReponseOctopush(
			@JsonProperty("sms_ticket") String smsTicket,
			@JsonProperty("number_of_contacts") Integer numberOfContacts,
			@JsonProperty("total_cost") BigDecimal totalCost) {
	}

	/** Corps d'erreur 400 : {@code {"code": 121, "message": "..."}}. */
	record ErreurOctopush(Integer code, String message) {
	}
}
