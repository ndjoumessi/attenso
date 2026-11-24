package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.courriel.ServiceCourriel;
import ai.attenso.motdepasse.ServiceReinitialisation;
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
 * Récupération de mot de passe.
 *
 * <p>Deux propriétés comptent plus que le reste, et les deux sont testées
 * pour de bon : <strong>la réponse ne dit jamais si le compte existe</strong>,
 * et <strong>un lien ne sert qu'une fois</strong>.
 */
@AutoConfigureMockMvc
@Import(MotDePasseIntegrationTest.PosteFactice.Config.class)
class MotDePasseIntegrationTest extends SocleIntegration {

	/** Une boîte aux lettres en mémoire : les tests n'envoient rien. */
	static class PosteFactice implements ServiceCourriel {

		record Message(String destinataire, String sujet, String corps) {
		}

		private final List<Message> boite = new ArrayList<>();

		@Override
		public boolean envoyer(String destinataire, String sujet, String corps) {
			boite.add(new Message(destinataire, sujet, corps));
			return true;
		}

		@Override
		public boolean actif() {
			return true;
		}

		List<Message> boite() {
			return List.copyOf(boite);
		}

		void vider() {
			boite.clear();
		}

		@TestConfiguration
		static class Config {

			@Bean
			@Primary
			PosteFactice posteFactice() {
				return new PosteFactice();
			}

			/**
			 * Un exécuteur qui exécute sur place.
			 *
			 * <p>La demande d'oubli travaille hors du fil de la requête — c'est
			 * ce qui rend son temps de réponse constant, donc muet sur
			 * l'existence du compte. Les tests, eux, ont besoin de savoir que
			 * c'est fini pour regarder la boîte aux lettres : les laisser
			 * attendre introduirait une course et des échecs aléatoires, qui
			 * finissent toujours par être relancés plutôt que lus.
			 */
			@Bean
			@Primary
			ai.attenso.courriel.TravailDiffere travailSurPlace() {
				return Runnable::run;
			}

			/**
			 * Une horloge qu'on avance à la main.
			 *
			 * <p>Nécessaire parce que l'instant d'émission d'un JWT est tronqué
			 * à la SECONDE : avec l'horloge système, connexion et
			 * réinitialisation tombent dans la même seconde, et l'on ne peut
			 * plus distinguer « avant » de « après ». Une attente d'une
			 * seconde ferait la même chose en plus lent et moins sûr.
			 *
			 * <p>Elle part de l'instant réel, et non d'une date arbitraire : le
			 * validateur de jetons de Spring compare, lui, à l'horloge système,
			 * et une heure inventée ferait rejeter des jetons parfaitement
			 * valables.
			 */
		}
	}

	private static final String MDP = "un-mot-de-passe-assez-long";

	private static final String NOUVEAU = "un-nouveau-mot-de-passe-long";

	@Autowired
	MockMvc http;

	@Autowired
	PosteFactice poste;

	@Autowired
	ServiceReinitialisation reinitialisations;


	@Autowired
	tools.jackson.databind.ObjectMapper json;

	private UUID tenantId;

	@BeforeEach
	void installer() {
		poste.vider();
		tenantId = creerClinique("clinique-oubli", "Clinique de l'Oubli", 2, 180);
		creerUtilisateur(tenantId, "asv@oubli.fr", MDP, Role.ACCUEIL);
	}

	private void demander(String slug, String email) throws Exception {
		http.perform(post("/api/connexion/oubli").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"%s","email":"%s"}
								""".formatted(slug, email)))
				.andExpect(status().isNoContent());
	}

	/** Le jeton tel qu'il voyage : extrait du lien contenu dans le courriel. */
	private String jetonRecu() {
		String corps = poste.boite().get(poste.boite().size() - 1).corps();
		int debut = corps.indexOf("/reinitialisation/") + "/reinitialisation/".length();
		return corps.substring(debut, corps.indexOf('\n', debut)).trim();
	}

	private boolean reinitialiser(String jeton, String motDePasse) throws Exception {
		String corps = http.perform(post("/api/connexion/reinitialiser")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"jeton":"%s","motDePasse":"%s"}
								""".formatted(jeton, motDePasse)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(corps).get("fait").asBoolean();
	}

	private boolean connexionReussie(String motDePasse) throws Exception {
		return http.perform(post("/api/connexion").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"clinique-oubli","email":"asv@oubli.fr",
								 "motDePasse":"%s"}
								""".formatted(motDePasse)))
				.andReturn().getResponse().getStatus() == 200;
	}

	@Test
	@DisplayName("le lien reçu permet de poser un nouveau mot de passe")
	void leLienPermetDeChangerLeMotDePasse() throws Exception {
		demander("clinique-oubli", "asv@oubli.fr");

		assertThat(poste.boite()).singleElement()
				.satisfies(message -> assertThat(message.destinataire())
						.isEqualTo("asv@oubli.fr"));
		assertThat(reinitialiser(jetonRecu(), NOUVEAU)).isTrue();

		assertThat(connexionReussie(NOUVEAU)).isTrue();
		assertThat(connexionReussie(MDP)).isFalse();
	}

	@Test
	@DisplayName("la réponse est la même pour un compte inconnu, et rien n'est envoyé")
	void aucuneEnumerationDeComptes() throws Exception {
		// LE test qui compte. Un message différent transformerait ce formulaire
		// en annuaire : on essaie des adresses, et l'on apprend lesquelles sont
		// clientes. Le code de réponse doit donc être identique.
		demander("clinique-oubli", "inconnu@ailleurs.fr");
		demander("clinique-qui-nexiste-pas", "asv@oubli.fr");

		assertThat(poste.boite()).isEmpty();
	}

	@Test
	@DisplayName("un lien ne sert qu'une fois")
	void leLienEstAUsageUnique() throws Exception {
		demander("clinique-oubli", "asv@oubli.fr");
		String jeton = jetonRecu();
		assertThat(reinitialiser(jeton, NOUVEAU)).isTrue();

		// Un lien traîne dans une boîte aux lettres, dans un historique de
		// navigateur, parfois dans les journaux d'un serveur de messagerie.
		assertThat(reinitialiser(jeton, "encore-un-autre-mot-de-passe")).isFalse();
		assertThat(connexionReussie(NOUVEAU)).isTrue();
	}

	@Test
	@DisplayName("une nouvelle demande annule la précédente")
	void laNouvelleDemandeAnnuleLAncienne() throws Exception {
		demander("clinique-oubli", "asv@oubli.fr");
		String ancien = jetonRecu();
		demander("clinique-oubli", "asv@oubli.fr");
		String recent = jetonRecu();

		// Trois demandes faites dans la panique laisseraient sinon trois liens
		// actifs, dont deux que personne ne surveille.
		assertThat(reinitialiser(ancien, NOUVEAU)).isFalse();
		assertThat(reinitialiser(recent, NOUVEAU)).isTrue();
	}

	@Test
	@DisplayName("un jeton inventé ne donne rien")
	void unJetonInventeNeDonneRien() throws Exception {
		assertThat(reinitialiser("jeton-totalement-invente", NOUVEAU)).isFalse();
	}

	@Test
	@DisplayName("le jeton n'est jamais stocké en clair")
	void leJetonNestPasStockeEnClair() throws Exception {
		demander("clinique-oubli", "asv@oubli.fr");
		String jeton = jetonRecu();

		// Une base lue par un tiers — sauvegarde égarée, injection, curiosité
		// d'un exploitant — donnerait sinon la main sur tous les comptes dont
		// une réinitialisation est en cours.
		Integer enClair = proprietaire.queryForObject(
				"SELECT count(*) FROM reinitialisation WHERE jeton_hash = ?",
				Integer.class, jeton);
		assertThat(enClair).isZero();
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM reinitialisation WHERE tenant_id = ?",
				Integer.class, tenantId)).isEqualTo(1);
	}

	@Test
	@DisplayName("au-delà de la limite, la demande est ignorée EN SILENCE")
	void laLimiteEstSilencieuse() throws Exception {
		for (int essai = 0; essai < 3; essai++) {
			demander("clinique-oubli", "asv@oubli.fr");
		}

		// Le quatrième est refusé, et c'est volontairement indiscernable du
		// reste : un 429 dirait « cette adresse existe et on lui a déjà
		// écrit », ce qui ramènerait l'énumération par une autre porte.
		demander("clinique-oubli", "asv@oubli.fr");

		assertThat(poste.boite()).hasSize(3);
	}

	@Test
	@DisplayName("la réinitialisation ferme les sessions déjà ouvertes")
	void laReinitialisationFermeLesSessions() throws Exception {
		// LE cas qui justifie tout ça : on change son mot de passe PARCE QU'ON
		// LE CROIT VOLÉ. Un mot de passe changé qui laisse la session du
		// voleur ouverte douze heures de plus ne sert à rien.
		String jetonAvant = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"clinique-oubli","email":"asv@oubli.fr",
								 "motDePasse":"%s"}
								""".formatted(MDP)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String jeton = json.readTree(jetonAvant).get("jeton").asText();
		http.perform(get("/api/file").header("Authorization", "Bearer " + jeton))
				.andExpect(status().isOk());

		demander("clinique-oubli", "asv@oubli.fr");
		assertThat(reinitialiser(jetonRecu(), NOUVEAU)).isTrue();

		http.perform(get("/api/file").header("Authorization", "Bearer " + jeton))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("une session ouverte après le changement reste valable")
	void uneSessionPosterieureResteValable() throws Exception {
		demander("clinique-oubli", "asv@oubli.fr");
		assertThat(reinitialiser(jetonRecu(), NOUVEAU)).isTrue();

        // Le pendant du test précédent : une révocation qui coupe AUSSI les
        // sessions ouvertes ensuite empêcherait simplement de se reconnecter.
		String corps = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"clinique-oubli","email":"asv@oubli.fr",
								 "motDePasse":"%s"}
								""".formatted(NOUVEAU)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		http.perform(get("/api/file")
						.header("Authorization", "Bearer "
								+ json.readTree(corps).get("jeton").asText()))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("la page de réinitialisation est servie sans authentification")
	void laPageEstPublique() throws Exception {
		http.perform(get("/reinitialisation/nimporte-quoi")).andExpect(status().isOk());
	}
}
