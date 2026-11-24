package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.compte.ServiceCompte;
import ai.attenso.motdepasse.MotDePasseDao;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.utilisateur.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Changer son mot de passe depuis le tableau.
 *
 * <p>Le seul chemin existant passait par « mot de passe oublié » : se
 * déconnecter, demander un lien, attendre un courriel. Sur une tablette de
 * comptoir partagée, ce détour suffisait à ne jamais le faire — or c'est le
 * geste qu'on veut facile le jour où quelqu'un part en connaissant le mot de
 * passe commun.
 */
@AutoConfigureMockMvc
class CompteIntegrationTest extends SocleIntegration {

	private static final String SLUG = "cerisiers";

	private static final String EMAIL = "asv@cerisiers.fr";

	private static final String ANCIEN = "un-mot-de-passe-assez-long";

	private static final String NOUVEAU = "un-autre-mot-de-passe-long";

	@Autowired
	MockMvc http;

	@Autowired
	ObjectMapper json;

	@Autowired
	ServiceCompte comptes;

	@Autowired
	MotDePasseDao motsDePasseDao;

	private UUID tenantId;

	@BeforeEach
	void installer() {
		tenantId = creerClinique(SLUG, "Clinique des Cerisiers", 2, 180);
		creerUtilisateur(tenantId, EMAIL, ANCIEN, Role.ACCUEIL);
	}

	private String connecter(String motDePasse) throws Exception {
		String corps = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"%s","email":"%s","motDePasse":"%s"}
								""".formatted(SLUG, EMAIL, motDePasse)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(corps).get("jeton").asText();
	}

	private org.springframework.test.web.servlet.ResultActions changer(String jeton,
			String actuel, String nouveau) throws Exception {
		return http.perform(post("/api/compte/mot-de-passe")
				.header("Authorization", "Bearer " + jeton)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"actuel":"%s","nouveau":"%s"}
						""".formatted(actuel, nouveau)));
	}

	@Test
	@DisplayName("celui qui change son mot de passe reste connecté, les autres non")
	void celuiQuiChangeResteConnecte() throws Exception {
		String tablette = connecter(ANCIEN);
		// Une seconde tablette de la même personne, connectée avant le
		// changement : c'est elle qu'on veut voir tomber.
		String autreTablette = connecter(ANCIEN);

		String corps = changer(tablette, ANCIEN, NOUVEAU)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String neuf = json.readTree(corps).get("jeton").asText();

		http.perform(get("/api/compte").header("Authorization", "Bearer " + neuf))
				.andExpect(status().isOk());
		http.perform(get("/api/compte").header("Authorization", "Bearer " + autreTablette))
				.andExpect(status().isUnauthorized());
		// Et l'ancien jeton de la tablette qui a fait le changement est mort
		// lui aussi : c'est le même compteur. Sans le jeton neuf rendu par la
		// réponse, changer son mot de passe se solderait par une déconnexion.
		http.perform(get("/api/compte").header("Authorization", "Bearer " + tablette))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("le nouveau mot de passe ouvre la connexion, l'ancien ne l'ouvre plus")
	void leNouveauMotDePasseOuvreLaConnexion() throws Exception {
		changer(connecter(ANCIEN), ANCIEN, NOUVEAU).andExpect(status().isOk());

		assertThat(connecter(NOUVEAU)).isNotBlank();
		http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"%s","email":"%s","motDePasse":"%s"}
								""".formatted(SLUG, EMAIL, ANCIEN)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("un mot de passe actuel faux est refusé en 403, et ne change rien")
	void leMotDePasseActuelFauxEstRefuse() throws Exception {
		String jeton = connecter(ANCIEN);

		// 403 et non 401 : un 401 ferait croire à la page que la session a
		// expiré, et elle renverrait vers l'écran de connexion.
		changer(jeton, "ce-n-est-pas-le-bon", NOUVEAU).andExpect(status().isForbidden());

		// Et rien n'a bougé : ni le mot de passe, ni la session en cours.
		assertThat(connecter(ANCIEN)).isNotBlank();
		http.perform(get("/api/compte").header("Authorization", "Bearer " + jeton))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("reposer le même mot de passe est refusé plutôt qu'accepté sans effet")
	void reposerLeMemeMotDePasseEstRefuse() throws Exception {
		String jeton = connecter(ANCIEN);

		// Accepter fermerait toutes les autres sessions pour rien.
		changer(jeton, ANCIEN, ANCIEN).andExpect(status().isBadRequest());

		http.perform(get("/api/compte").header("Authorization", "Bearer " + jeton))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("un mot de passe trop court est refusé, comme à l'inscription")
	void unMotDePasseTropCourtEstRefuse() throws Exception {
		changer(connecter(ANCIEN), ANCIEN, "court").andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("les essais sur le mot de passe actuel sont comptés")
	void lesEssaisSontComptes() throws Exception {
		String jeton = connecter(ANCIEN);

		for (int essai = 0; essai < 5; essai++) {
			changer(jeton, "faux-" + essai, NOUVEAU).andExpect(status().isForbidden());
		}
		// Le sixième ne va même pas jusqu'à la vérification : celui qui tient
		// une session ouverte ne doit pas pouvoir deviner le mot de passe, qui
		// est la seule chose survivant à l'expiration du jeton.
		changer(jeton, "faux-6", NOUVEAU).andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("le compte dit qui est connecté, adresse et rôle")
	void leCompteDitQuiEstConnecte() throws Exception {
		String corps = http.perform(get("/api/compte")
						.header("Authorization", "Bearer " + connecter(ANCIEN)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(json.readTree(corps).get("email").asString()).isEqualTo(EMAIL);
		assertThat(json.readTree(corps).get("role").asString()).isEqualTo("ACCUEIL");
	}

	// --- La fenêtre entre le contrôle du filtre et l'écriture --------------
	//
	// Le filtre vérifie la version du jeton à l'entrée. Entre ce contrôle et
	// l'écriture, un gérant peut rétrograder ou désactiver le compte. Ces deux
	// cas se construisent ici en SQL direct, sans toucher au compteur de
	// version : c'est exactement l'état que la course produirait.

	@Test
	@DisplayName("le jeton réémis porte le rôle de la BASE, pas celui du jeton d'avant")
	void leJetonReemisPorteLeRoleDeLaBase() throws Exception {
		UUID id = creerUtilisateur(tenantId, "gerant@cerisiers.fr", ANCIEN, Role.GERANT);
		// Rétrogradé pendant qu'il est à l'écran, sans que sa session tombe.
		proprietaire.update("UPDATE utilisateur SET role = 'ACCUEIL' WHERE id = ?", id);

		var etat = ContexteTenant.avec(tenantId,
				() -> comptes.changerMotDePasse(id, ANCIEN, NOUVEAU));

		// Recopier le rôle du jeton d'appel aurait rendu un jeton GERANT neuf,
		// valide douze heures, et renouvelable à chaque changement de mot de
		// passe : la rétrogradation n'aurait jamais pris effet.
		assertThat(etat.role()).isEqualTo(Role.ACCUEIL);
	}

	@Test
	@DisplayName("le mot de passe d'un compte désactivé n'est pas écrit")
	void leMotDePasseDUnCompteDesactiveNEstPasEcrit() {
		UUID id = creerUtilisateur(tenantId, "parti@cerisiers.fr", ANCIEN, Role.ACCUEIL);
		String avant = empreinteDe(id);
		proprietaire.update("UPDATE utilisateur SET actif = false WHERE id = ?", id);

		var pose = ContexteTenant.avec(tenantId, () -> motsDePasseDao.poserLeMotDePasse(
				id, "peu-importe", java.time.Instant.now()));

		assertThat(pose).isEmpty();
		assertThat(empreinteDe(id)).isEqualTo(avant);
	}

	private String empreinteDe(UUID id) {
		return proprietaire.queryForObject("SELECT mdp_hash FROM utilisateur WHERE id = ?",
				String.class, id);
	}

	@Test
	@DisplayName("sans jeton, le compte n'est pas servi")
	void sansJetonRienNEstServi() throws Exception {
		http.perform(get("/api/compte")).andExpect(status().isUnauthorized());
		http.perform(post("/api/compte/mot-de-passe")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"actuel":"%s","nouveau":"%s"}
								""".formatted(ANCIEN, NOUVEAU)))
				.andExpect(status().isUnauthorized());
	}
}
