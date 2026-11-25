package ai.attenso.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.paiement.PasserelleFausse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La page d'inscription doit savoir, AVANT toute saisie, si elle peut inscrire.
 *
 * <p>Sans réglage Stripe, {@code ServiceInscription.inscrire} appelle la
 * passerelle avant la moindre écriture, celle-ci lève, et la personne reçoit un
 * 503 — après avoir rempli le nom, l'adresse, un mot de passe de douze
 * caractères, choisi sa formule et accepté les conditions. La vitrine lui
 * affirme pourtant que « l'inscription prend trois minutes », et son appel à
 * l'action principal mène là.
 *
 * <p>L'information existait depuis le début
 * ({@code PasserellePaiement.disponible()}) ; elle n'était jamais dite à la
 * page.
 *
 * <p>Les DEUX états sont éprouvés. Un test qui n'aurait vérifié que l'ouverture
 * serait resté vert si le drapeau était câblé en dur à {@code true} — c'est-à-
 * dire précisément dans le cas qui a produit le défaut.
 */
@AutoConfigureMockMvc
@Import(PasserelleFausse.Config.class)
class OuvertureInscriptionIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	PasserelleFausse passerelle;

	@AfterEach
	void reparer() {
		passerelle.reparer();
	}

	@Test
	@DisplayName("les paliers disent que la souscription est ouverte quand elle l'est")
	void ouverteQuandLaPasserelleRepond() throws Exception {
		http.perform(get("/api/inscription/paliers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ouverte").value(true))
				.andExpect(jsonPath("$.paliers").isArray())
				.andExpect(jsonPath("$.paliers[0].nom").exists())
				.andExpect(jsonPath("$.paliers[0].prixCentimes").isNumber());
	}

	@Test
	@DisplayName("les paliers disent que la souscription est fermée quand elle l'est")
	void fermeeQuandLaPasserelleNeRepondPas() throws Exception {
		// L'état du déploiement de production aujourd'hui : aucun réglage Stripe.
		passerelle.tomberEnPanne();

		http.perform(get("/api/inscription/paliers"))
				.andExpect(status().isOk())
				// Les prix restent servis : la page doit pouvoir les afficher
				// pour dire ce qu'on vend, même quand on ne peut pas le vendre.
				.andExpect(jsonPath("$.ouverte").value(false))
				.andExpect(jsonPath("$.paliers[0].prixCentimes").isNumber());
	}
}
