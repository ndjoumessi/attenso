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
@TestPropertySource(properties = "attenso.file.tickets-max-par-jour=2")
class VerificationNumeroIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	ServiceTenant cliniques;

	@MockitoBean
	VerificateurNumero verificateur;

	private void enregistrer(String slug, String telephone, int statutAttendu) throws Exception {
		http.perform(post("/t/" + slug + "/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"telephone\": \"%s\"}".formatted(telephone)))
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
								{"telephone": "06 11 11 11 11"}"""))
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

		enregistrer("hlr-inconnu", "06 11 11 11 11", 201);

		assertThat(compterNotifications(id, "TICKET"))
				.as("et le SMS part quand même : on ne sait pas qu'il n'arrivera pas")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("le deuxième ticket du même numéro réutilise le verdict déjà "
			+ "payé — une famille avec deux animaux ne coûte pas deux HLR")
	void leSecondTicketReutiliseLeVerdict() throws Exception {
		creerClinique("hlr-famille", "Clinique", 2, 180);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.joignable("live"));

		enregistrer("hlr-famille", "06 11 11 11 11", 201);
		enregistrer("hlr-famille", "06 11 11 11 11", 201);

		verify(verificateur, times(1)).verifier("+33611111111");
	}

	@Test
	@DisplayName("plafond du jour atteint : le HLR n'est JAMAIS appelé — sinon "
			+ "on brûlerait des crédits sans même créer de ticket")
	void lePlafondProtegeAussiLesCreditsHlr() throws Exception {
		creerClinique("hlr-plafond", "Clinique", 2, 180);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.joignable("live"));

		enregistrer("hlr-plafond", "06 11 11 11 11", 201);
		enregistrer("hlr-plafond", "06 22 22 22 22", 201);
		org.mockito.Mockito.clearInvocations(verificateur);

		enregistrer("hlr-plafond", "06 33 33 33 33", 429);

		verify(verificateur, never()).verifier(anyString());
	}

	@Test
	@DisplayName("troisième ticket pour un même numéro : refusé sans appeler le HLR")
	void laLimiteParNumeroProtegeAussiLesCredits() throws Exception {
		creerClinique("hlr-limite", "Clinique", 2, 180);
		when(verificateur.verifier(anyString()))
				.thenReturn(ResultatVerification.joignable("live"));

		enregistrer("hlr-limite", "06 11 11 11 11", 201);
		enregistrer("hlr-limite", "06 11 11 11 11", 201);
		org.mockito.Mockito.clearInvocations(verificateur);

		enregistrer("hlr-limite", "06 11 11 11 11", 429);

		verify(verificateur, never()).verifier(anyString());
	}
}
