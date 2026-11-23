package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import ai.attenso.utilisateur.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** Le parcours complet, par HTTP. */
@AutoConfigureMockMvc
class ApiIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	ObjectMapper json;

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Test
	@DisplayName("un client s'enregistre depuis le QR et suit son tour sans compte")
	void parcoursClient() throws Exception {
		creerClinique("cerisiers", "Clinique des Cerisiers", 2, 180);

		String corps = http.perform(post("/t/cerisiers/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"telephone": "06 11 11 11 11"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.position").value(1))
				.andReturn().getResponse().getContentAsString();

		JsonNode ticket = json.readTree(corps);
		String reference = ticket.get("reference").asText();
		assertThat(reference).hasSize(12);
		assertThat(ticket.get("lien").asText())
				.as("le lien doit rester court : il est compté dans les 160 "
						+ "caractères du SMS")
				.hasSizeLessThan(45);

		http.perform(get("/api/suivi/" + reference))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clinique").value("Clinique des Cerisiers"))
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.statut").value("PREVENU"));
	}

	@Test
	@DisplayName("un numéro qui n'est pas un mobile français est refusé avant tout SMS")
	void numeroRefuse() throws Exception {
		creerClinique("refus", "Clinique", 2, 180);
		http.perform(post("/t/refus/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"telephone": "01 42 00 00 00"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Numéro invalide"));
		assertThat(compterNotifications(cliniques.parSlug("refus").id(), "TICKET")).isZero();
	}

	@Test
	@DisplayName("une référence inconnue ne révèle rien")
	void referenceInconnue() throws Exception {
		http.perform(get("/api/suivi/zzzzzzzzzzzz"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value(
						org.hamcrest.Matchers.not(org.hamcrest.Matchers
								.containsString("zzzzzzzzzzzz"))));
	}

	@Test
	@DisplayName("le lien SMS de la veille n'ouvre plus rien, et ne se "
			+ "distingue pas d'une référence inconnue")
	void leLienDeLaVeilleEstClos() throws Exception {
		creerClinique("veille", "Clinique", 2, 180);
		String corps = http.perform(post("/t/veille/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"telephone": "06 11 11 11 11"}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String reference = json.readTree(corps).get("reference").asText();

		http.perform(get("/api/suivi/" + reference)).andExpect(status().isOk());

		// La ligne ticket survit à la journée — c'est elle qui porte le
		// compteur de départs — donc rien n'expire tout seul. Sans contrôle de
		// date, le lien reçu par SMS resterait ouvert indéfiniment.
		proprietaire.update("UPDATE ticket SET jour = jour - 1 WHERE reference = ?",
				reference);

		http.perform(get("/api/suivi/" + reference))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Ticket introuvable"));
		http.perform(get("/api/suivi/" + reference + "/flux"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("le tableau de l'accueil exige un jeton, et porte le compteur de départs")
	void tableauDeLAccueil() throws Exception {
		UUID clinique = creerClinique("accueil", "Clinique", 2, 180);
		creerUtilisateur(clinique, "asv@clinique.fr", "mot-de-passe-solide", Role.ACCUEIL);

		http.perform(get("/api/file")).andExpect(status().isUnauthorized());

		String jeton = connecter("accueil", "asv@clinique.fr", "mot-de-passe-solide");

		Ticket un = ContexteTenant.avec(clinique, () -> creation.creerDepuisQr(
				cliniques.parSlug("accueil"), file.jourCourant(cliniques.parSlug("accueil")),
				"0611111111"));

		http.perform(get("/api/file").header("Authorization", "Bearer " + jeton))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enFile").value(1))
				.andExpect(jsonPath("$.departs").value(0));

		http.perform(post("/api/tickets/" + un.id() + "/parti")
						.header("Authorization", "Bearer " + jeton)
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("PARTI"));

		http.perform(get("/api/file").header("Authorization", "Bearer " + jeton))
				.andExpect(jsonPath("$.departs").value(1))
				.andExpect(jsonPath("$.enFile").value(0));
	}

	@Test
	@DisplayName("le jeton d'une clinique ne touche pas au ticket d'une autre")
	void unJetonNeFranchitPasLeCloisonnement() throws Exception {
		UUID alpha = creerClinique("alpha", "Alpha", 2, 180);
		UUID beta = creerClinique("beta", "Beta", 2, 180);
		creerUtilisateur(alpha, "asv@alpha.fr", "mot-de-passe-solide", Role.ACCUEIL);

		Ticket chezBeta = ContexteTenant.avec(beta, () -> creation.creerDepuisQr(
				cliniques.parSlug("beta"), file.jourCourant(cliniques.parSlug("beta")),
				"0622222222"));

		String jetonAlpha = connecter("alpha", "asv@alpha.fr", "mot-de-passe-solide");

		http.perform(post("/api/tickets/" + chezBeta.id() + "/appeler")
						.header("Authorization", "Bearer " + jetonAlpha))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("une transition impossible rend 409 avec l'état courant, "
			+ "pour que la tablette se recale")
	void transitionImpossible() throws Exception {
		UUID clinique = creerClinique("conflit", "Clinique", 2, 180);
		creerUtilisateur(clinique, "asv@clinique.fr", "mot-de-passe-solide", Role.ACCUEIL);
		String jeton = connecter("conflit", "asv@clinique.fr", "mot-de-passe-solide");

		Ticket un = ContexteTenant.avec(clinique, () -> creation.creerDepuisQr(
				cliniques.parSlug("conflit"), file.jourCourant(cliniques.parSlug("conflit")),
				"0611111111"));

		http.perform(post("/api/tickets/" + un.id() + "/termine")
						.header("Authorization", "Bearer " + jeton))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.statutCourant").value("PREVENU"));
	}

	@Test
	@DisplayName("l'écran du mur n'expose que des numéros")
	void ecranDuMur() throws Exception {
		UUID clinique = creerClinique("mur", "Clinique du Mur", 2, 180);
		ContexteTenant.avec(clinique, () -> creation.creerDepuisQr(
				cliniques.parSlug("mur"), file.jourCourant(cliniques.parSlug("mur")),
				"0611111111"));

		String corps = http.perform(get("/api/ecran/mur"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clinique").value("Clinique du Mur"))
				.andReturn().getResponse().getContentAsString();

		assertThat(corps)
				.as("aucun numéro de téléphone, aucun identifiant de ticket au mur")
				.doesNotContain("0611111111").doesNotContain("+33")
				.doesNotContain("reference");
	}

	@Test
	@DisplayName("des identifiants faux donnent le même message qu'un compte inconnu")
	void connexionRefusee() throws Exception {
		UUID clinique = creerClinique("refus-connexion", "Clinique", 2, 180);
		creerUtilisateur(clinique, "asv@clinique.fr", "mot-de-passe-solide", Role.ACCUEIL);

		String inconnu = echecConnexion("refus-connexion", "personne@clinique.fr", "x");
		String mauvaisMdp = echecConnexion("refus-connexion", "asv@clinique.fr", "faux");
		assertThat(inconnu)
				.as("sinon le message d'erreur énumère les comptes existants")
				.isEqualTo(mauvaisMdp);
	}

	private String connecter(String slug, String email, String motDePasse) throws Exception {
		String corps = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content(identifiants(slug, email, motDePasse)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(corps).get("jeton").asText();
	}

	private String echecConnexion(String slug, String email, String motDePasse)
			throws Exception {
		String corps = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content(identifiants(slug, email, motDePasse)))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(corps).get("detail").asText();
	}

	private String identifiants(String slug, String email, String motDePasse) {
		return """
				{"slug": "%s", "email": "%s", "motDePasse": "%s"}"""
				.formatted(slug, email, motDePasse);
	}
}
