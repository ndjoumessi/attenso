package ai.attenso.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import ai.attenso.config.ProprietesOctopush;
import ai.attenso.config.ProprietesSms;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * L'adaptateur contre un serveur simulé. Aucun appel réel : chaque envoi vers
 * le vrai Octopush coûte de l'argent, et un test qui en dépense est un test
 * qu'on finit par désactiver.
 */
class ExpediteurSmsOctopushTest {

	private static final String URL = "https://api.octopush.test/v1/public/sms-campaign/send";

	private MockRestServiceServer octopush;

	private ExpediteurSmsOctopush expediteur;

	private UUID reference;

	@BeforeEach
	void preparer() {
		RestClient.Builder constructeur = RestClient.builder()
				.baseUrl("https://api.octopush.test")
				.defaultHeader("api-login", "compte@clinique.fr")
				.defaultHeader("api-key", "cle-de-test");
		this.octopush = MockRestServiceServer.bindTo(constructeur).build();
		this.expediteur = new ExpediteurSmsOctopush(constructeur.build(),
				new ProprietesOctopush("https://api.octopush.test", "compte@clinique.fr",
						"cle-de-test", "sms_premium", "alert", false,
						Duration.ofSeconds(5), Duration.ofSeconds(15)),
				new ProprietesSms("octopush", new BigDecimal("4.5")));
		this.reference = UUID.randomUUID();
	}

	private MessageSms message() {
		return new MessageSms(reference, "+33611111111", "Cerisiers",
				"Clinique des Cerisiers : c'est presque à vous, merci de revenir. "
						+ "https://attenso.fr/s/aZ9k2Lm4Qp8x");
	}

	@Test
	@DisplayName("un envoi réussi rend le ticket de campagne et les deux coûts")
	void envoiReussi() {
		octopush.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("api-login", "compte@clinique.fr"))
				.andExpect(header("api-key", "cle-de-test"))
				.andRespond(withStatus(HttpStatus.CREATED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"sms_ticket":"sms_5f3fbce61266e","number_of_contacts":1,
								 "total_cost":0.045}"""));

		ResultatEnvoi resultat = expediteur.envoyer(message());

		octopush.verify();
		assertThat(resultat.reussi()).isTrue();
		assertThat(resultat.fournisseurId()).isEqualTo("sms_5f3fbce61266e");
		assertThat(resultat.coutCentimes())
				.as("notre estimation : un segment au tarif négocié")
				.isEqualByComparingTo("4.5");
		assertThat(resultat.coutFournisseur())
				.as("et le chiffre brut de l'agrégateur, gardé à côté")
				.isEqualByComparingTo("0.045");
	}

	@Test
	@DisplayName("la requête porte l'identifiant de notification en clé "
			+ "d'idempotence — sans quoi un réessai paierait le SMS deux fois")
	void laRequetePorteLaCleDIdempotence() {
		octopush.expect(requestTo(URL))
				.andExpect(jsonPath("$.request_id").value(reference.toString()))
				.andExpect(jsonPath("$.sender").value("Cerisiers"))
				.andExpect(jsonPath("$.purpose").value("alert"))
				.andExpect(jsonPath("$.type").value("sms_premium"))
				.andExpect(jsonPath("$.simulation_mode").value(false))
				.andExpect(jsonPath("$.recipients[0].phone_number").value("+33611111111"))
				.andRespond(withStatus(HttpStatus.CREATED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"sms_ticket":"sms_1","number_of_contacts":1,"total_cost":0.045}"""));

		expediteur.envoyer(message());

		octopush.verify();
	}

	@Test
	@DisplayName("un expéditeur non conforme n'atteint même pas le réseau")
	void expediteurNonConformeNePartPas() {
		octopush.expect(ExpectedCount.never(), requestTo(URL));

		ResultatEnvoi resultat = expediteur.envoyer(new MessageSms(reference, "+33611111111",
				"Clinique des Cerisiers", "peu importe"));

		octopush.verify();
		assertThat(resultat.reussi()).isFalse();
		assertThat(resultat.erreur()).contains("non conforme");
	}

	@Test
	@DisplayName("le refus de l'API garde SON message, pas un code HTTP nu")
	void leRefusGardeLeMessageDeLApi() {
		octopush.expect(requestTo(URL))
				.andRespond(withBadRequest()
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"code":121,"message":"Sender is not allowed."}"""));

		ResultatEnvoi resultat = expediteur.envoyer(message());

		octopush.verify();
		assertThat(resultat.reussi()).isFalse();
		assertThat(resultat.erreur())
				.as("« expéditeur non autorisé » est ce qu'il faut lire six semaines plus tard")
				.contains("Sender is not allowed")
				.contains("121");
	}

	@Test
	@DisplayName("une réponse sans ticket de campagne est un échec, pas un succès muet")
	void reponseSansTicketEstUnEchec() {
		octopush.expect(requestTo(URL))
				.andRespond(withStatus(HttpStatus.CREATED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"number_of_contacts\":1}"));

		ResultatEnvoi resultat = expediteur.envoyer(message());

		assertThat(resultat.reussi()).isFalse();
		assertThat(resultat.erreur()).contains("sans identifiant");
	}

	@Test
	@DisplayName("le corps envoyé est bien le texte du message, intact")
	void leTexteEstTransmisIntact() {
		octopush.expect(requestTo(URL))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"c'est presque")))
				.andRespond(withStatus(HttpStatus.CREATED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("""
								{"sms_ticket":"sms_2","number_of_contacts":1,"total_cost":0.045}"""));

		expediteur.envoyer(message());

		octopush.verify();
	}
}
