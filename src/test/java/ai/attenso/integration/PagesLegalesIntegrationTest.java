package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.facturation.Palier;
import ai.attenso.inscription.DemandeInscription;
import ai.attenso.inscription.ServiceInscription;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ce qu'il faut publier pour avoir le droit de vendre.
 *
 * <p>Ces pages ne servent à rien si elles ne sont pas ATTEIGNABLES : une page
 * légale parfaite derrière un 404, ou absente du pied de page, ne protège
 * personne. D'où des tests sur les routes et sur les liens, et pas seulement
 * sur l'existence des fichiers.
 */
@AutoConfigureMockMvc
class PagesLegalesIntegrationTest extends SocleIntegration {

	private static final Path VITRINE =
			Path.of("src/main/resources/static/app/vitrine.html");

	@Autowired
	MockMvc http;

	@Autowired
	ServiceInscription inscriptions;

	@Test
	@DisplayName("les trois pages légales sont servies sans authentification")
	void lesPagesSontPubliques() throws Exception {
		for (String route : new String[] { "/mentions-legales", "/cgv",
				"/confidentialite" }) {
			http.perform(get(route)).andExpect(status().isOk());
		}
	}

	@Test
	@DisplayName("le pied de page de la vitrine mène aux trois")
	void lePiedDePageYMene() throws Exception {
		// Sans ce test, une page peut exister, être servie, et rester
		// introuvable — ce qui revient au même pour le visiteur.
		String vitrine = Files.readString(VITRINE, StandardCharsets.UTF_8);
		assertThat(vitrine)
				.contains("href=\"/mentions-legales\"")
				.contains("href=\"/cgv\"")
				.contains("href=\"/confidentialite\"");
	}

	@Test
	@DisplayName("l'identité de l'éditeur est publique, comme la loi l'exige")
	void lIdentiteEstPublique() throws Exception {
		http.perform(get("/api/editeur"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.denomination").value("Attenso (tests)"))
				.andExpect(jsonPath("$.hebergeur").isNotEmpty());
	}

	@Test
	@DisplayName("l'inscription est refusée sans acceptation des conditions")
	void sansAcceptationPasDInscription() throws Exception {
		// La case HTML se contourne en trois secondes : c'est le serveur qui
		// doit refuser, sinon l'acceptation n'est qu'un décor.
		http.perform(post("/api/inscription").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nom":"Clinique Sans CGV","email":"a@scgv.fr",
								 "motDePasse":"un-mot-de-passe-assez-long",
								 "palier":"COMPTOIR","avecEcran":false,
								 "cgvAcceptees":false}
								"""))
				.andExpect(status().isBadRequest());

		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM tenant WHERE slug LIKE 'clinique-sans-cgv%'",
				Integer.class)).isZero();
	}

	@Test
	@DisplayName("l'acceptation est horodatée, parce qu'une case ne prouve rien seule")
	void lAcceptationEstHorodatee() {
		var reponse = inscriptions.inscrire(new DemandeInscription("Clinique Conforme",
				"a@conforme.fr", "un-mot-de-passe-assez-long", Palier.COMPTOIR.name(),
				false, true));

		assertThat(proprietaire.queryForObject(
				"SELECT cgv_acceptees_le FROM tenant WHERE slug = ?",
				java.sql.Timestamp.class, reponse.slug())).isNotNull();
	}

	@Test
	@DisplayName("les pages légales ne chargent rien d'un autre domaine")
	void aucuneRequeteTierce() throws Exception {
		// Même exigence que pour le reste du site : une page qui promet de ne
		// rien envoyer à des tiers ne peut pas charger une police chez l'un
		// d'eux. Le lien vers la CNIL est une NAVIGATION, pas un chargement.
		for (String route : new String[] { "/mentions-legales", "/cgv",
				"/confidentialite" }) {
			String page = http.perform(get(route)).andReturn().getResponse()
					.getContentAsString();
			assertThat(page)
					.as("la page %s ne doit charger aucune ressource tierce", route)
					.doesNotContain("src=\"http").doesNotContain("href=\"https://fonts");
		}
	}
}
