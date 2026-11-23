package ai.attenso.numero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ai.attenso.config.ProprietesHlr;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * L'interprétation du HLR, contre un serveur simulé.
 *
 * <p>Ces tests gardent une asymétrie délibérée : refuser à tort renvoie du
 * comptoir un client qui est venu, accepter à tort coûte 4,5 centimes. On ne
 * refuse donc que sur un négatif certain, et chaque cas ambigu laisse entrer.
 */
class VerificateurNumeroOctopushTest {

	private static final String URL = "https://api.octopush.test/v1/public/contact/hlr-request";

	private MockRestServiceServer octopush;

	private VerificateurNumeroOctopush verificateur;

	@BeforeEach
	void preparer() {
		construire();
	}

	/**
	 * Un serveur simulé neuf : MockRestServiceServer refuse qu'on lui ajoute
	 * une attente après le premier appel, et plusieurs cas se vérifient mieux
	 * dans un seul test que dispersés en huit.
	 */
	private void construire() {
		RestClient.Builder constructeur = RestClient.builder()
				.baseUrl("https://api.octopush.test");
		this.octopush = MockRestServiceServer.bindTo(constructeur).build();
		this.verificateur = new VerificateurNumeroOctopush(constructeur.build(),
				new ProprietesHlr("octopush", Duration.ofSeconds(3), new BigDecimal("0.5")));
	}

	private void repond(String lineStatus, String status, String type) {
		octopush.expect(requestTo(URL)).andRespond(withSuccess("""
				[{"phone_number":"+33611111111","status":"%s","line_status":"%s",
				  "phone_number_type":"%s","network_name":"Free"}]"""
				.formatted(status, lineStatus, type), MediaType.APPLICATION_JSON));
	}

	/** Un cas complet : serveur neuf, réponse posée, verdict rendu. */
	private Verdict verdictPour(String lineStatus, String status, String type) {
		construire();
		repond(lineStatus, status, type);
		return verificateur.verifier("+33611111111").verdict();
	}

	@Test
	@DisplayName("ligne vivante et attribuée : joignable")
	void ligneVivante() {
		repond("live", "valid", "mobile");
		assertThat(verificateur.verifier("+33611111111").verdict()).isEqualTo(Verdict.JOIGNABLE);
	}

	@Test
	@DisplayName("ligne morte : injoignable, et le client peut corriger au comptoir")
	void ligneMorte() {
		repond("dead", "invalid", "mobile");
		ResultatVerification resultat = verificateur.verifier("+33611111111");
		assertThat(resultat.verdict()).isEqualTo(Verdict.INJOIGNABLE);
		assertThat(resultat.detail()).contains("dead");
	}

	@Test
	@DisplayName("LE cas qui compte : téléphone éteint ou hors couverture — on "
			+ "laisse entrer, le SMS arrivera au rallumage")
	void telephoneEteintNEstPasUnRefus() {
		repond("absent_subscriber", "valid", "mobile");
		assertThat(verificateur.verifier("+33611111111").verdict())
				.as("la SIM existe ; refuser renverrait du comptoir quelqu'un dont "
						+ "le portable dort dans sa poche")
				.isEqualTo(Verdict.INDETERMINE);
	}

	@Test
	@DisplayName("un fixe ou un format illisible : injoignable")
	void appareilsSansSms() {
		assertThat(verdictPour("live", "valid", "landline")).isEqualTo(Verdict.INJOIGNABLE);
		assertThat(verdictPour("not_applicable", "unknown", "bad_format"))
				.isEqualTo(Verdict.INJOIGNABLE);
	}

	@Test
	@DisplayName("une SIM sans service de messagerie : injoignable")
	void simDonneesSeules() {
		repond("no_teleservice_provisioned", "valid", "mobile");
		assertThat(verificateur.verifier("+33611111111").verdict())
				.isEqualTo(Verdict.INJOIGNABLE);
	}

	@Test
	@DisplayName("tout ce que l'opérateur ne sait pas trancher laisse entrer")
	void lAmbiguiteLaisseEntrer() {
		for (String[] cas : new String[][] {
				{ "no_coverage", "unknown", "mobile" },
				{ "inconclusive", "unknown", "mobile" },
				{ "not_available_network_only", "valid", "mobile" },
				{ "live", "operator_is_not_available", "mobile" },
		}) {
			assertThat(verdictPour(cas[0], cas[1], cas[2]))
					.as("%s / %s / %s", cas[0], cas[1], cas[2])
					.isEqualTo(Verdict.INDETERMINE);
		}
	}

	@Test
	@DisplayName("un numéro VoIP que l'opérateur déclare vivant et attribué est "
			+ "joignable : « voip » ne rend pas la réponse ambiguë")
	void voipDeclareVivantEstJoignable() {
		// Les deux verdicts laisseraient passer de toute façon ; seule
		// l'étiquette enregistrée dans telephone_valide diffère, et elle doit
		// dire ce que l'opérateur a dit.
		assertThat(verdictPour("live", "valid", "voip")).isEqualTo(Verdict.JOIGNABLE);
	}

	@Test
	@DisplayName("une panne du fournisseur n'empêche JAMAIS d'émettre un ticket")
	void unePanneLaisseEntrer() {
		construire();
		octopush.expect(requestTo(URL)).andRespond(withServerError());
		assertThat(verificateur.verifier("+33611111111").verdict())
				.as("agrégateur en panne").isEqualTo(Verdict.INDETERMINE);

		construire();
		octopush.expect(requestTo(URL)).andRespond(withBadRequest());
		assertThat(verificateur.verifier("+33611111111").verdict())
				.as("requête refusée").isEqualTo(Verdict.INDETERMINE);

		construire();
		octopush.expect(requestTo(URL)).andRespond(withSuccess("[]",
				MediaType.APPLICATION_JSON));
		assertThat(verificateur.verifier("+33611111111").verdict())
				.as("réponse vide").isEqualTo(Verdict.INDETERMINE);
	}

	@Test
	@DisplayName("la requête envoie bien le numéro en format international")
	void formeDeLaRequete() {
		octopush.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.phone_numbers[0]").value("+33611111111"))
				.andRespond(withSuccess("""
						[{"phone_number":"+33611111111","status":"valid",
						  "line_status":"live","phone_number_type":"mobile"}]""",
						MediaType.APPLICATION_JSON));

		verificateur.verifier("+33611111111");

		octopush.verify();
	}
}
