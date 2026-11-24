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
		String jetonEcran = appairerEcran(clinique, "ecranDuMur123");
		ContexteTenant.avec(clinique, () -> creation.creerDepuisQr(
				cliniques.parSlug("mur"), file.jourCourant(cliniques.parSlug("mur")),
				"0611111111"));

		String corps = http.perform(get("/api/ecran/jeton/" + jetonEcran))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clinique").value("Clinique du Mur"))
				.andReturn().getResponse().getContentAsString();

		assertThat(corps)
				.as("aucun numéro de téléphone, aucun identifiant de ticket au mur")
				.doesNotContain("0611111111").doesNotContain("+33")
				.doesNotContain("reference");
	}

	@Test
	@DisplayName("l'écran du mur n'est plus atteignable par le slug de la clinique")
	void ecranNonAtteignableParSlug() throws Exception {
		UUID clinique = creerClinique("mur-prive", "Clinique Privée", 2, 180);
		appairerEcran(clinique, "jetonDuMurPrive");

		// Le slug est devinable — c'est le nom de la clinique. L'affluence
		// d'un cabinet est une donnée commerciale : elle ne se sert pas à qui
		// la demande.
		//
		// On vérifie l'ABSENCE de donnée, pas un code précis : la route n'a
		// plus de gestionnaire, et le code rendu dépend alors de la chaîne de
		// sécurité — 404 sous MockMvc, 401 sur le serveur réel. Figer l'un des
		// deux ferait passer ce test pour la mauvaise raison.
		var reponse = http.perform(get("/api/ecran/mur-prive")).andReturn().getResponse();
		assertThat(reponse.getStatus()).isNotEqualTo(200);
		assertThat(reponse.getContentAsString()).doesNotContain("Clinique Privée");
	}

	@Test
	@DisplayName("une clinique sans écran ne répond sur aucune route d'écran (EF-603)")
	void cliniqueSansEcran() throws Exception {
		UUID clinique = creerClinique("sans-ecran", "Clinique Sans Écran", 2, 180);
		ContexteTenant.avec(clinique, () -> creation.creerDepuisQr(
				cliniques.parSlug("sans-ecran"),
				file.jourCourant(cliniques.parSlug("sans-ecran")), "0611111111"));

		// Rien à désactiver : rien n'est activé. Ici la route EXISTE, donc le
		// 404 est bien celui du contrôleur et peut être affirmé tel quel.
		http.perform(get("/api/ecran/jeton/sans-ecran")).andExpect(status().isNotFound());
	}

    @Test
	@DisplayName("un jeton d'écran inconnu et une clinique sans écran rendent la "
			+ "MÊME réponse")
	void memeReponsePourJetonFauxEtCliniqueSansEcran() throws Exception {
		UUID clinique = creerClinique("ecran-sonde", "Clinique Sondée", 2, 180);
		appairerEcran(clinique, "leVraiJetonIci");

		// Distinguer les deux dirait à qui sonde laquelle des deux il vient de
		// toucher, donc quelles cliniques ont un écran.
		int jetonFaux = http.perform(get("/api/ecran/jeton/unJetonQuiNExistePas"))
				.andReturn().getResponse().getStatus();
		int sansEcran = http.perform(get("/api/ecran/jeton/ecran-sonde"))
				.andReturn().getResponse().getStatus();

		assertThat(jetonFaux).isEqualTo(sansEcran).isEqualTo(404);
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

	@Test
	@DisplayName("le formulaire du comptoir peut nommer le responsable de "
			+ "traitement, et rien d'autre (EC-04)")
	void nomDeLaCliniquePourLaMention() throws Exception {
		creerClinique("mention", "Clinique des Cerisiers", 2, 180);

		String corps = http.perform(get("/t/mention/clinique"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nom").value("Clinique des Cerisiers"))
				.andReturn().getResponse().getContentAsString();

		// Le nom est déjà imprimé sur l'affiche qui porte le QR : l'exposer ne
		// révèle rien. Le reste de la configuration de la clinique, si.
		assertThat(corps).doesNotContain("expediteur").doesNotContain("jeton")
				.doesNotContain("seuil").doesNotContain("id");
	}

	@Test
	@DisplayName("l'enregistrement anonyme n'écrit AUCUNE adresse IP : EC-03 "
			+ "limite la collecte au mobile et au numéro de ticket")
	void lEnregistrementNeCollectePasDAdresseIp() throws Exception {
		UUID clinique = creerClinique("minimisation", "Clinique", 2, 180);

		http.perform(post("/t/minimisation/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Forwarded-For", "203.0.113.7")
						.content("{\"telephone\":\"0611111111\"}"))
				.andExpect(status().isCreated());

		// La colonne journal_acces.ip existe et reste vide : ce n'est PAS un
		// oubli. La remplir ajouterait une troisième donnée personnelle, que
		// EC-03 interdit. Ce test est là pour qu'on ne la « répare » pas.
		Integer avecIp = proprietaire.queryForObject("""
				SELECT count(*)::int FROM journal_acces
				WHERE tenant_id = ? AND ip IS NOT NULL
				""", Integer.class, clinique);
		assertThat(avecIp).as("aucune adresse IP ne doit être consignée").isZero();

		Integer actions = proprietaire.queryForObject("""
				SELECT count(*)::int FROM journal_acces WHERE tenant_id = ?
				""", Integer.class, clinique);
		assertThat(actions).as("l'action, elle, est bien tracée (ENF-07)").isPositive();
	}
}
