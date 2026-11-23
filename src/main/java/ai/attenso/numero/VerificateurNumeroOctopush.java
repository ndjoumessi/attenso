package ai.attenso.numero;

import ai.attenso.config.ProprietesHlr;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Vérification HLR par Octopush.
 *
 * <p>L'opérateur répond deux champs qu'il ne faut surtout pas confondre.
 * {@code line_status = dead} dit que la ligne n'existe pas ;
 * {@code absent_subscriber} dit que la SIM existe mais que le téléphone est
 * éteint ou hors couverture. Traiter le second comme le premier reviendrait à
 * renvoyer du comptoir un client dont le portable dort dans sa poche — la
 * situation la plus banale d'une salle d'attente.
 *
 * <p>D'où la règle de ce fichier : <strong>on ne refuse que sur un négatif
 * certain</strong>. Tout le reste laisse entrer, y compris une panne de
 * l'agrégateur ou un dépassement de délai. Un SMS perdu coûte 4,5 centimes, un
 * client renvoyé coûte le client.
 */
@Component
@ConditionalOnProperty(name = "attenso.hlr.verificateur", havingValue = "octopush")
public class VerificateurNumeroOctopush implements VerificateurNumero {

	private static final Logger JOURNAL = LoggerFactory.getLogger(VerificateurNumeroOctopush.class);

	/** Les seuls états de ligne qui établissent qu'aucun SMS n'arrivera. */
	private static final Set<String> LIGNES_MORTES = Set.of(
			"dead", "no_teleservice_provisioned", "not_applicable");

	/** Les seuls types d'appareil qui ne reçoivent pas de SMS, de façon certaine. */
	private static final Set<String> TYPES_SANS_SMS = Set.of(
			"landline", "bad_format", "pager", "voicemail_only");

	private final RestClient client;

	private final ProprietesHlr proprietes;

	public VerificateurNumeroOctopush(@Qualifier("clientOctopushHlr") RestClient client,
			ProprietesHlr proprietes) {
		this.client = client;
		this.proprietes = proprietes;
	}

	@Override
	public ResultatVerification verifier(String telephoneE164) {
		try {
			List<ReponseHlr> reponses = client.post()
					.uri("/v1/public/contact/hlr-request")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("phone_numbers", List.of(telephoneE164)))
					.retrieve()
					.body(new org.springframework.core.ParameterizedTypeReference<>() { });

			if (reponses == null || reponses.isEmpty()) {
				return ResultatVerification.indetermine("réponse HLR vide");
			}
			return interpreter(reponses.get(0));
		}
		catch (RestClientException panne) {
			// Panne ou délai dépassé : on laisse entrer. Faire attendre
			// quelqu'un au comptoir parce que notre fournisseur est lent serait
			// un très mauvais échange.
			JOURNAL.warn("vérification HLR indisponible, le ticket est émis sans elle : {}",
					panne.getMessage());
			return ResultatVerification.indetermine("HLR indisponible");
		}
	}

	ResultatVerification interpreter(ReponseHlr reponse) {
		String etatLigne = minuscule(reponse.lineStatus());
		String type = minuscule(reponse.phoneNumberType());
		String statut = minuscule(reponse.status());
		String detail = "status=%s line_status=%s type=%s".formatted(statut, etatLigne, type);

		if (TYPES_SANS_SMS.contains(type)) {
			return ResultatVerification.injoignable(detail);
		}
		if (LIGNES_MORTES.contains(etatLigne)) {
			return ResultatVerification.injoignable(detail);
		}
		// « invalid » seul ne suffit pas : il dit que la ligne n'a pas de
		// titulaire déclaré, ce qui arrive sur des lignes actives. On exige que
		// l'état de ligne le confirme, ce que le test ci-dessus a déjà fait.
		if ("live".equals(etatLigne) && "valid".equals(statut)) {
			return ResultatVerification.joignable(detail);
		}
		// absent_subscriber, no_coverage, inconclusive, operator_is_not_available…
		// la SIM peut très bien exister et le message arriver plus tard.
		return ResultatVerification.indetermine(detail);
	}

	private String minuscule(String valeur) {
		return valeur == null ? "" : valeur.toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public boolean payant() {
		return true;
	}

	public java.math.BigDecimal coutCentimes() {
		return proprietes.coutCentimes();
	}

	/** Une entrée de la réponse HLR. Les clés sont en snake_case. */
	public record ReponseHlr(
			@com.fasterxml.jackson.annotation.JsonProperty("phone_number") String phoneNumber,
			@com.fasterxml.jackson.annotation.JsonProperty("status") String status,
			@com.fasterxml.jackson.annotation.JsonProperty("line_status") String lineStatus,
			@com.fasterxml.jackson.annotation.JsonProperty("phone_number_type")
			String phoneNumberType,
			@com.fasterxml.jackson.annotation.JsonProperty("network_name") String networkName) {
	}
}
