package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.numero.ResultatVerification;
import ai.attenso.numero.VerificateurNumero;
import ai.attenso.tenant.ServiceTenant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La vérification du numéro dans le parcours réel, avec le vérificateur
 * remplacé : un test qui appelle le vrai HLR dépense de l'argent à chaque
 * exécution.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"attenso.file.tickets-max-par-jour=2",
		"attenso.hlr.max-par-jour=2",
})
class VerificationNumeroIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	ServiceTenant cliniques;

	@MockitoBean
	VerificateurNumero verificateur;

	/**
	 * Chaque test parle depuis sa propre adresse : le limiteur de débit est un
	 * singleton du contexte, et des tests qui se partagent une adresse se
	 * pollueraient l'un l'autre.
	 */
	private void enregistrer(String slug, String telephone, int statutAttendu, String adresse)
			throws Exception {
		http.perform(post("/t/" + slug + "/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"telephone\": \"%s\"}".formatted(telephone))
						.with(requete -> {
							requete.setRemoteAddr(adresse);
							return requete;
						}))
				.andExpect(status().is(statutAttendu));
	}

	@Test
	@DisplayName("un numéro que l'opérateur déclare mort est refusé au comptoir, "
			+ "avec un message qui invite à corriger")
	void unNumeroMortEstRefuseAuComptoir() throws Exception {
		UUID id = creerClinique("hlr-mort", "Clinique", 2, 180);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.injoignable("line_status=dead"));

		http.perform(post("/t/hlr-mort/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"telephone": "06 11 11 11 11"}""")
						.with(requete -> {
							requete.setRemoteAddr("10.0.0.9");
							return requete;
						}))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Numéro injoignable"))
				.andExpect(jsonPath("$.detail")
						.value(org.hamcrest.Matchers.containsString("Vérifiez-le")));

		assertThat(compterNotifications(id, "TICKET"))
				.as("aucun SMS payé pour un numéro dont on sait qu'il n'arrivera pas")
				.isZero();
	}

	@Test
	@DisplayName("un verdict indéterminé laisse entrer : le téléphone est "
			+ "peut-être simplement éteint")
	void unVerdictIndetermineLaisseEntrer() throws Exception {
		UUID id = creerClinique("hlr-inconnu", "Clinique", 2, 180);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.indetermine("absent_subscriber"));

		enregistrer("hlr-inconnu", "06 11 11 11 11", 201, "10.0.0.1");

		assertThat(compterNotifications(id, "TICKET"))
				.as("et le SMS part quand même : on ne sait pas qu'il n'arrivera pas")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("le deuxième ticket du même numéro réutilise le verdict déjà "
			+ "payé — une famille avec deux animaux ne coûte pas deux HLR")
	void leSecondTicketReutiliseLeVerdict() throws Exception {
		creerClinique("hlr-famille", "Clinique", 2, 180);
		when(verificateur.payant()).thenReturn(true);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.joignable("live"));

		enregistrer("hlr-famille", "06 11 11 11 11", 201, "10.0.0.2");
		enregistrer("hlr-famille", "06 11 11 11 11", 201, "10.0.0.2");

		verify(verificateur, times(1)).verifier("+33611111111");
	}

	@Test
	@DisplayName("plafond du jour atteint : le HLR n'est JAMAIS appelé — sinon "
			+ "on brûlerait des crédits sans même créer de ticket")
	void lePlafondProtegeAussiLesCreditsHlr() throws Exception {
		creerClinique("hlr-plafond", "Clinique", 2, 180);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.joignable("live"));

		enregistrer("hlr-plafond", "06 11 11 11 11", 201, "10.0.0.3");
		enregistrer("hlr-plafond", "06 22 22 22 22", 201, "10.0.0.3");
		org.mockito.Mockito.clearInvocations(verificateur);

		enregistrer("hlr-plafond", "06 33 33 33 33", 429, "10.0.0.3");

		verify(verificateur, never()).verifier(anyString());
	}

	@Test
	@DisplayName("LE trou de la première version : un numéro refusé ne crée pas "
			+ "de ticket, donc un plafond de TICKETS ne plafonnait rien")
	void unNumeroRefuseNEstVerifieQuUneFois() throws Exception {
		creerClinique("hlr-oracle", "Clinique", 2, 180);
		when(verificateur.payant()).thenReturn(true);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.injoignable("line_status=dead"));

		enregistrer("hlr-oracle", "06 11 11 11 11", 400, "10.0.0.5");
		enregistrer("hlr-oracle", "06 11 11 11 11", 400, "10.0.0.5");
		enregistrer("hlr-oracle", "06 11 11 11 11", 400, "10.0.0.5");

		verify(verificateur, times(1)).verifier("+33611111111")
				;
	}

	@Test
	@DisplayName("budget de vérifications épuisé : on n'appelle plus, mais les "
			+ "tickets continuent d'être émis")
	void leBudgetEpuiseNArretePasLaSalleDAttente() throws Exception {
		creerClinique("hlr-budget", "Clinique", 2, 300);
		when(verificateur.payant()).thenReturn(true);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.injoignable("dead"));

		// Deux numéros refusés : aucun ticket créé, mais le budget de deux
		// vérifications est consommé.
		enregistrer("hlr-budget", "06 11 11 11 11", 400, "10.0.0.6");
		enregistrer("hlr-budget", "06 22 22 22 22", 400, "10.0.0.7");
		org.mockito.Mockito.clearInvocations(verificateur);

		// Le troisième numéro n'est plus vérifié — et il passe.
		enregistrer("hlr-budget", "06 33 33 33 33", 201, "10.0.0.8");

		verify(verificateur, never()).verifier(anyString());
	}

	@Test
	@DisplayName("trop de refus depuis une adresse : plus de réponse, et plus "
			+ "d'appel payant — l'oracle se referme")
	void lesRefusRepetesFermentLOracle() throws Exception {
		creerClinique("hlr-refus", "Clinique", 2, 180);
		// Vérificateur gratuit ici, à dessein : la limite sur les refus protège
		// l'INFORMATION, le budget protège l'ARGENT. Les deux doivent tenir
		// séparément, et ce test ne doit pas s'arrêter au budget.
		when(verificateur.payant()).thenReturn(false);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.injoignable("dead"));

		for (int i = 1; i <= 5; i++) {
			enregistrer("hlr-refus", "06 11 11 11 1" + i, 400, "10.0.0.66");
		}
		org.mockito.Mockito.clearInvocations(verificateur);

		enregistrer("hlr-refus", "06 11 11 11 19", 429, "10.0.0.66");

		verify(verificateur, never()).verifier(anyString());
	}

	@Test
	@DisplayName("troisième ticket pour un même numéro : refusé sans appeler le HLR")
	void laLimiteParNumeroProtegeAussiLesCredits() throws Exception {
		creerClinique("hlr-limite", "Clinique", 2, 180);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.joignable("live"));

		enregistrer("hlr-limite", "06 11 11 11 11", 201, "10.0.0.4");
		enregistrer("hlr-limite", "06 11 11 11 11", 201, "10.0.0.4");
		org.mockito.Mockito.clearInvocations(verificateur);

		enregistrer("hlr-limite", "06 11 11 11 11", 429, "10.0.0.4");

		verify(verificateur, never()).verifier(anyString());
	}
}
