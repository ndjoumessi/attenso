package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.courriel.ServiceCourriel;
import ai.attenso.courriel.TravailDiffere;
import ai.attenso.equipe.MembreEquipe;
import ai.attenso.equipe.ServiceEquipe;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.utilisateur.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Les comptes du personnel.
 *
 * <p>Jusqu'ici une clinique n'avait qu'un compte : l'ASV et le vétérinaire
 * partageaient le même mot de passe, et un départ de salarié obligeait à tout
 * réinitialiser.
 */
@AutoConfigureMockMvc
@Import(EquipeIntegrationTest.Config.class)
class EquipeIntegrationTest extends SocleIntegration {

	/** Boîte aux lettres en mémoire : aucun courriel ne part en test. */
	static class Boite implements ServiceCourriel {

		final List<String> destinataires = new ArrayList<>();

		@Override
		public boolean envoyer(String destinataire, String sujet, String corps) {
			destinataires.add(destinataire);
			return true;
		}

		@Override
		public boolean actif() {
			return true;
		}
	}

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		Boite boite() {
			return new Boite();
		}

		@Bean
		@Primary
		TravailDiffere surPlace() {
			return Runnable::run;
		}
	}

	private static final String MDP = "un-mot-de-passe-assez-long";

	@Autowired
	MockMvc http;

	@Autowired
	ServiceEquipe equipe;

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	Boite boite;

	@Autowired
	tools.jackson.databind.ObjectMapper json;

	private UUID tenantId;

	private UUID gerantId;

	@BeforeEach
	void installer() {
		boite.destinataires.clear();
		tenantId = creerClinique("clinique-equipe", "Clinique de l'Équipe", 2, 180);
		gerantId = creerUtilisateur(tenantId, "gerant@equipe.fr", MDP, Role.GERANT);
	}

	private String connecter(String email, String motDePasse) throws Exception {
		String corps = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"clinique-equipe","email":"%s","motDePasse":"%s"}
								""".formatted(email, motDePasse)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(corps).get("jeton").asText();
	}

	// --- Inviter --------------------------------------------------------

	@Test
	@DisplayName("un compte invité reçoit un lien et ne peut pas encore se connecter")
	void leCompteInviteAttendSonLien() {
		MembreEquipe invite = ContexteTenant.avec(tenantId,
				() -> equipe.inviter(cliniques.parId(tenantId), "Asv@Equipe.FR",
						Role.ACCUEIL));

		assertThat(invite.email()).isEqualTo("asv@equipe.fr");
		// Le gérant a besoin de voir CE drapeau : un compte créé mais jamais
		// ouvert ressemble en tout point à un compte qui marche.
		assertThat(invite.aChoisiSonMotDePasse()).isFalse();
		assertThat(boite.destinataires).containsExactly("asv@equipe.fr");
	}

	@Test
	@DisplayName("aucun mot de passe ne peut ouvrir un compte en attente d'invitation")
	void aucunMotDePasseNOuvreUnCompteEnAttente() throws Exception {
		ContexteTenant.executer(tenantId,
				() -> equipe.inviter(cliniques.parId(tenantId), "asv@equipe.fr",
						Role.ACCUEIL));

		// Le marqueur posé à la place de l'empreinte n'est pas une empreinte
		// valable : rien ne peut y correspondre, pas même lui-même.
		// Un mot de passe vide est refusé plus tôt, par la validation du
		// formulaire : ce n'est pas ce qu'on teste ici.
		for (String essai : new String[] { MDP, "!invitation-en-attente" }) {
			http.perform(post("/api/connexion").contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"slug":"clinique-equipe","email":"asv@equipe.fr",
									 "motDePasse":"%s"}
									""".formatted(essai)))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("deux fois la même adresse est refusée")
	void deuxFoisLaMemeAdresse() throws Exception {
		String jeton = connecter("gerant@equipe.fr", MDP);
		inviterParHttp(jeton, "asv@equipe.fr", "ACCUEIL").andExpect(status().isCreated());

		inviterParHttp(jeton, "ASV@equipe.fr", "GERANT").andExpect(status().isConflict());
	}

	private org.springframework.test.web.servlet.ResultActions inviterParHttp(String jeton,
			String email, String role) throws Exception {
		return http.perform(post("/api/equipe").header("Authorization", "Bearer " + jeton)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","role":"%s"}
						""".formatted(email, role)));
	}

	// --- Droits ---------------------------------------------------------

	@Test
	@DisplayName("l'accueil n'a pas accès aux comptes, le gérant oui")
	void seulLeGerantGereLesComptes() throws Exception {
		creerUtilisateur(tenantId, "asv@equipe.fr", MDP, Role.ACCUEIL);

		http.perform(get("/api/equipe")
						.header("Authorization", "Bearer " + connecter("asv@equipe.fr", MDP)))
				.andExpect(status().isForbidden());
		http.perform(get("/api/equipe")
						.header("Authorization", "Bearer " + connecter("gerant@equipe.fr", MDP)))
				.andExpect(status().isOk());
	}

	// --- Le garde du dernier gérant --------------------------------------

	@Test
	@DisplayName("le dernier gérant ne peut être ni rétrogradé ni désactivé")
	void leDernierGerantEstProtege() throws Exception {
		String jeton = connecter("gerant@equipe.fr", MDP);

		// Sans ce garde, une clinique se verrouille dehors en un clic : plus
		// personne pour inviter, ni pour gérer l'abonnement.
		http.perform(post("/api/equipe/" + gerantId + "/role")
						.header("Authorization", "Bearer " + jeton)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"ACCUEIL\"}"))
				.andExpect(status().isConflict());
		http.perform(post("/api/equipe/" + gerantId + "/desactiver")
						.header("Authorization", "Bearer " + jeton))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("avec un second gérant, le premier peut se retirer")
	void avecUnSecondGerantLePremierPeutPartir() throws Exception {
		String jeton = connecter("gerant@equipe.fr", MDP);
		UUID second = creerUtilisateur(tenantId, "second@equipe.fr", MDP, Role.GERANT);

		http.perform(post("/api/equipe/" + gerantId + "/desactiver")
						.header("Authorization", "Bearer " + jeton))
				.andExpect(status().isNoContent());

		assertThat(ContexteTenant.avec(tenantId, () -> equipe.lister(tenantId)))
				.filteredOn(MembreEquipe::actif)
				.extracting(MembreEquipe::id).containsExactly(second);
	}

	// --- La révocation ---------------------------------------------------

	@Test
	@DisplayName("désactiver un compte ferme sa session tout de suite")
	void desactiverFermeLaSession() throws Exception {
		UUID asv = creerUtilisateur(tenantId, "asv@equipe.fr", MDP, Role.ACCUEIL);
		String jetonAsv = connecter("asv@equipe.fr", MDP);
		http.perform(get("/api/file").header("Authorization", "Bearer " + jetonAsv))
				.andExpect(status().isOk());

		http.perform(post("/api/equipe/" + asv + "/desactiver")
						.header("Authorization", "Bearer " + connecter("gerant@equipe.fr", MDP)))
				.andExpect(status().isNoContent());

		// Un départ de salarié ne doit pas laisser une session ouverte
		// jusqu'au soir.
		http.perform(get("/api/file").header("Authorization", "Bearer " + jetonAsv))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("changer un rôle ferme aussi la session, car le rôle voyage dans le jeton")
	void changerLeRoleFermeLaSession() throws Exception {
		UUID second = creerUtilisateur(tenantId, "second@equipe.fr", MDP, Role.GERANT);
		String jetonSecond = connecter("second@equipe.fr", MDP);
		http.perform(get("/api/equipe").header("Authorization", "Bearer " + jetonSecond))
				.andExpect(status().isOk());

		http.perform(post("/api/equipe/" + second + "/role")
						.header("Authorization", "Bearer " + connecter("gerant@equipe.fr", MDP))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"ACCUEIL\"}"))
				.andExpect(status().isNoContent());

		// Sans l'invalidation, la rétrogradation ne prendrait effet qu'à
		// l'expiration du jeton — jusqu'à douze heures plus tard, alors qu'on
		// rétrograde précisément pour retirer un droit maintenant.
		http.perform(get("/api/equipe").header("Authorization", "Bearer " + jetonSecond))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("un compte réactivé peut se reconnecter")
	void unCompteReactivePeutRevenir() throws Exception {
		UUID asv = creerUtilisateur(tenantId, "asv@equipe.fr", MDP, Role.ACCUEIL);
		String jetonGerant = connecter("gerant@equipe.fr", MDP);
		http.perform(post("/api/equipe/" + asv + "/desactiver")
						.header("Authorization", "Bearer " + jetonGerant))
				.andExpect(status().isNoContent());

		http.perform(post("/api/equipe/" + asv + "/reactiver")
						.header("Authorization", "Bearer " + jetonGerant))
				.andExpect(status().isNoContent());

		http.perform(get("/api/file")
						.header("Authorization", "Bearer " + connecter("asv@equipe.fr", MDP)))
				.andExpect(status().isOk());
	}

	// --- Cloisonnement ---------------------------------------------------

	@Test
	@DisplayName("un gérant ne voit que les comptes de SA clinique")
	void lesComptesSontCloisonnes() throws Exception {
		UUID autre = creerClinique("autre-clinique", "Autre", 2, 180);
		creerUtilisateur(autre, "gerant@autre.fr", MDP, Role.GERANT);

		String corps = http.perform(get("/api/equipe")
						.header("Authorization", "Bearer " + connecter("gerant@equipe.fr", MDP)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(corps).contains("gerant@equipe.fr").doesNotContain("gerant@autre.fr");
	}
}
