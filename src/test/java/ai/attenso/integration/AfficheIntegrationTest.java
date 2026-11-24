package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.affiche.LecteurQr;
import ai.attenso.affiche.Qr;
import ai.attenso.utilisateur.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * L'affiche du comptoir (EF-101).
 *
 * <p>C'est la pièce par laquelle le produit démarre : sans QR imprimé, aucun
 * client n'entre dans la file. Une clinique qui s'inscrit seule n'a personne
 * pour venir le lui poser, donc l'affiche doit sortir du produit — et le QR
 * qu'elle porte doit mener à CETTE clinique, pas à une autre.
 */
@AutoConfigureMockMvc
class AfficheIntegrationTest extends SocleIntegration {

	private static final String MDP = "un-mot-de-passe-assez-long";

	@Autowired
	MockMvc http;

	@Autowired
	ObjectMapper json;

	private String connecter(String slug, String email) throws Exception {
		String corps = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"%s","email":"%s","motDePasse":"%s"}
								""".formatted(slug, email, MDP)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(corps).get("jeton").asText();
	}

	private JsonNode affiche(String jeton) throws Exception {
		return json.readTree(http.perform(get("/api/affiche")
						.header("Authorization", "Bearer " + jeton))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	@DisplayName("le QR de l'affiche se décode et mène au comptoir de cette clinique")
	void leQrMeneAuComptoirDeCetteClinique() throws Exception {
		UUID tenantId = creerClinique("cerisiers", "Clinique des Cerisiers", 2, 180);
		creerUtilisateur(tenantId, "gerant@cerisiers.fr", MDP, Role.GERANT);

		JsonNode vue = affiche(connecter("cerisiers", "gerant@cerisiers.fr"));

		assertThat(vue.get("nom").asString()).isEqualTo("Clinique des Cerisiers");
		// La base de lien des tests, la même que celle des SMS.
		assertThat(vue.get("url").asString()).isEqualTo("https://attenso.test/t/cerisiers");
		// Et surtout : le tracé est vraiment un QR, et il dit vraiment cela.
		assertThat(LecteurQr.lire(qrDe(vue))).isEqualTo("https://attenso.test/t/cerisiers");
	}

	@Test
	@DisplayName("deux cliniques ne reçoivent pas la même affiche")
	void deuxCliniquesNOntPasLaMemeAffiche() throws Exception {
		UUID une = creerClinique("cerisiers", "Clinique des Cerisiers", 2, 180);
		creerUtilisateur(une, "gerant@cerisiers.fr", MDP, Role.GERANT);
		UUID autre = creerClinique("tilleuls", "Clinique des Tilleuls", 2, 180);
		creerUtilisateur(autre, "gerant@tilleuls.fr", MDP, Role.GERANT);

		JsonNode vue = affiche(connecter("tilleuls", "gerant@tilleuls.fr"));

		assertThat(LecteurQr.lire(qrDe(vue))).isEqualTo("https://attenso.test/t/tilleuls");
	}

	@Test
	@DisplayName("sans expéditeur SMS validé, l'affiche ne promet pas de message")
	void sansExpediteurValideRienNEstPromis() throws Exception {
		UUID tenantId = creerClinique("neuve", "Clinique Neuve", 2, 180, null);
		creerUtilisateur(tenantId, "gerant@neuve.fr", MDP, Role.GERANT);

		// L'état réel d'une clinique le jour de son inscription : l'expéditeur
		// n'est pas encore déposé auprès des opérateurs. Une affiche qui
		// promettrait un SMS ferait ranger le téléphone au client.
		assertThat(affiche(connecter("neuve", "gerant@neuve.fr")).get("sms").asBoolean())
				.isFalse();
	}

	@Test
	@DisplayName("l'accueil peut réimprimer l'affiche, ce n'est pas réservé au gérant")
	void lAccueilPeutReimprimerLAffiche() throws Exception {
		UUID tenantId = creerClinique("cerisiers", "Clinique des Cerisiers", 2, 180);
		creerUtilisateur(tenantId, "asv@cerisiers.fr", MDP, Role.ACCUEIL);

		assertThat(affiche(connecter("cerisiers", "asv@cerisiers.fr")).get("sms").asBoolean())
				.isTrue();
	}

	@Test
	@DisplayName("sans jeton, l'affiche n'est pas servie")
	void sansJetonRienNEstServi() throws Exception {
		http.perform(get("/api/affiche")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("la page de l'affiche est du balisage inerte, servie sans jeton")
	void laPageEstServieSansJeton() throws Exception {
		// Elle ne contient aucune donnée : c'est /api/affiche qui exige le
		// jeton. La servir derrière l'authentification demanderait au
		// navigateur de poser un en-tête sur une navigation.
		http.perform(get("/affiche")).andExpect(status().isOk());
	}

	private Qr qrDe(JsonNode vue) {
		JsonNode qr = vue.get("qr");
		return new Qr(qr.get("cote").asInt(), qr.get("chemin").asString());
	}
}
