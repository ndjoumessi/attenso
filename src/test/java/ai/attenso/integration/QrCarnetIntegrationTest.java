package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.affiche.LecteurQr;
import ai.attenso.affiche.Qr;
import ai.attenso.carnet.Animal;
import ai.attenso.carnet.ServiceCarnet;
import ai.attenso.tenant.ContexteTenant;
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
 * EF-906 promet un carnet « auquel son propriétaire accède en scannant un QR
 * code ». Le tableau disait « Émettre un QR de carnet », « Révoquer le QR » —
 * et n'affichait qu'une URL en texte brut. Aucun endpoint n'émettait ce QR :
 * la clinique n'avait aucun moyen de le donner.
 *
 * <p>À la demande et non dans la liste : encoder un QR par animal à chaque
 * lecture de `/api/animaux` ferait payer la grille entière pour un geste qui
 * porte sur un seul animal.
 */
@AutoConfigureMockMvc
class QrCarnetIntegrationTest extends SocleIntegration {

	private static final String MDP = "un-mot-de-passe-assez-long";

	@Autowired
	MockMvc http;

	@Autowired
	ObjectMapper json;

	@Autowired
	ServiceCarnet carnets;

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

	private Qr qrDe(JsonNode vue) {
		JsonNode qr = vue.get("qr");
		return new Qr(qr.get("cote").asInt(), qr.get("chemin").asString());
	}

	@Test
	@DisplayName("le QR du carnet se décode et mène au carnet de CET animal")
	void leQrMeneAuCarnetDeCetAnimal() throws Exception {
		UUID tenantId = creerClinique("qrcarnet", "Clinique", 2, 180);
		creerUtilisateur(tenantId, "gerant@qrcarnet.fr", MDP, Role.GERANT);
		ContexteTenant.lier(tenantId);
		Animal chat = carnets.creer(tenantId, "Mistigri", "Chat", "0611111111", true);
		String attendu = "https://attenso.test/carnet/" + chat.jetonCarnet();

		String corps = http.perform(get("/api/animaux/" + chat.id() + "/carnet/qr")
						.header("Authorization", "Bearer "
								+ connecter("qrcarnet", "gerant@qrcarnet.fr")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode vue = json.readTree(corps);

		assertThat(vue.get("url").asString()).isEqualTo(attendu);
		// Le tracé est vraiment un QR, et il dit vraiment CETTE adresse.
		assertThat(LecteurQr.lire(qrDe(vue))).isEqualTo(attendu);
	}

	@Test
	@DisplayName("un animal sans carnet n'a pas de QR à montrer")
	void sansCarnetPasDeQr() throws Exception {
		UUID tenantId = creerClinique("sansqr", "Clinique", 2, 180);
		creerUtilisateur(tenantId, "gerant@sansqr.fr", MDP, Role.GERANT);
		ContexteTenant.lier(tenantId);
		Animal chien = carnets.creer(tenantId, "Ulysse", "Chien", null, false);

		http.perform(get("/api/animaux/" + chien.id() + "/carnet/qr")
						.header("Authorization", "Bearer "
								+ connecter("sansqr", "gerant@sansqr.fr")))
				.andExpect(status().isNotFound());
	}
}
