package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.attenso.amorce.ReinitialisationDemonstration;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.TestPropertySource;

/**
 * La configuration d'une démonstration qu'on rejoue : tous les verrous levés,
 * et le vrai conteneur — la commande est le bean que Spring a construit, pas un
 * objet monté à la main.
 */
@TestPropertySource(properties = {
		"attenso.amorce.active=true",
		"attenso.amorce.demonstration=true",
		"attenso.amorce.reinitialiser=true",
		"attenso.amorce.slug=demo",
		"attenso.amorce.nom=Clinique de démonstration",
		"attenso.amorce.email=accueil@demo.attenso.test",
		"attenso.amorce.mot-de-passe=mdp-de-demonstration-de-test",
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.minimum-idle=1",
})
class ReinitialisationDemonstrationIntegrationTest extends ReinitialisationBase {

	@Autowired
	ReinitialisationDemonstration reinitialisation;

	@Autowired
	Clock horloge;

	private void lancer() {
		reinitialisation.run(new DefaultApplicationArguments(new String[0]));
	}

	private UUID cliniqueDeDemonstration() {
		return creerClinique("demo", "Clinique de démonstration", 2, 180);
	}

	private long compterLe(String table, UUID tenantId, LocalDate jour) {
		return proprietaire.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE tenant_id = ? AND jour = ?", Long.class,
				tenantId, Date.valueOf(jour));
	}

	@Test
	@DisplayName("efface ce que la démonstration a accumulé et remet une file du jour, une veille, un agenda et des carnets")
	void remetLaDemonstration() {
		UUID demo = cliniqueDeDemonstration();
		ReinitialisationBase.Marqueur marqueur = chargerUneClinique(demo);

		lancer();

		assertThat(existe("ticket", marqueur.ticket())).as("le ticket d'avant").isFalse();
		assertThat(existe("animal", marqueur.animal())).as("l'animal d'avant").isFalse();
		LocalDate aujourdhui = LocalDate.now(horloge);
		assertThat(compterLe("ticket", demo, aujourdhui)).as("la file du jour").isEqualTo(9);
		assertThat(compter("ticket", demo)).as("la file du jour et la veille").isEqualTo(19);
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM ticket WHERE tenant_id = ? AND statut = 'PARTI'", Long.class,
				demo)).as("les deux départs de la veille").isEqualTo(2);
		assertThat(compter("mesure_jour", demo)).as("la mesure de la veille").isEqualTo(1);
		assertThat(compter("rendez_vous", demo)).as("l'agenda").isEqualTo(3);
		assertThat(compter("plage_horaire", demo)).as("les horaires").isPositive();
		assertThat(compter("animal", demo)).as("les carnets").isEqualTo(2);
		assertThat(compter("vaccination", demo)).isEqualTo(5);
		assertThat(compter("notification", demo)).as("aucun SMS en attente d'une file effacée")
				.isZero();
	}

	@Test
	@DisplayName("lancée plusieurs fois de suite, elle ne double rien")
	void nEnDoublePasLaFile() {
		UUID demo = cliniqueDeDemonstration();
		chargerUneClinique(demo);

		lancer();
		Map<String, Long> apresLaPremiere = instantane(demo);
		lancer();
		lancer();

		assertThat(instantane(demo)).isEqualTo(apresLaPremiere);
		assertThat(apresLaPremiere.get("ticket")).isEqualTo(19);
		assertThat(apresLaPremiere.get("rendez_vous")).isEqualTo(3);
		assertThat(apresLaPremiere.get("animal")).isEqualTo(2);
	}

	@Test
	@DisplayName("la clinique, son compte, ses motifs et son écran survivent ; les journaux gardent leurs lignes")
	void laCliniqueSurvit() {
		UUID demo = cliniqueDeDemonstration();
		appairerEcran(demo, "jeton-ecran-de-la-demo");
		ReinitialisationBase.Marqueur marqueur = chargerUneClinique(demo);
		String motDePasse = proprietaire.queryForObject(
				"SELECT mdp_hash FROM utilisateur WHERE id = ?", String.class, marqueur.utilisateur());

		lancer();

		assertThat(idDe("demo")).as("le tenant, même identifiant").isEqualTo(demo);
		assertThat(proprietaire.queryForObject("SELECT jeton_ecran FROM tenant WHERE id = ?",
				String.class, demo)).isEqualTo("jeton-ecran-de-la-demo");
		assertThat(existe("utilisateur", marqueur.utilisateur())).as("le compte").isTrue();
		assertThat(proprietaire.queryForObject("SELECT mdp_hash FROM utilisateur WHERE id = ?",
				String.class, marqueur.utilisateur())).as("son mot de passe").isEqualTo(motDePasse);
		assertThat(proprietaire.queryForList(
				"SELECT libelle FROM motif_priorite WHERE tenant_id = ?", String.class, demo))
				.as("les motifs").contains("Motif propre à la clinique");
		// « En ajout seul » : les deux journaux gardent leur ligne, détachée du ticket.
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM journal_acces WHERE tenant_id = ? AND ticket_id IS NULL",
				Long.class, demo)).isEqualTo(1);
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM commande WHERE tenant_id = ? AND ticket_id IS NULL",
				Long.class, demo)).isEqualTo(1);
	}

	@Test
	@DisplayName("elle ne touche à aucune autre clinique de la base")
	void lesAutresCliniquesNeBougentPas() {
		UUID demo = cliniqueDeDemonstration();
		chargerUneClinique(demo);
		UUID voisine = creerClinique("voisine", "Clinique voisine", 2, 180);
		chargerUneClinique(voisine);
		Map<String, Long> avant = instantane(voisine);

		lancer();

		assertThat(instantane(voisine)).isEqualTo(avant);
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM motif_priorite WHERE tenant_id = ?", Long.class, voisine))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("un semis qui échoue rend la démonstration d'avant, pas une clinique vide")
	void toutOuRien() {
		UUID demo = cliniqueDeDemonstration();
		ReinitialisationBase.Marqueur marqueur = chargerUneClinique(demo);
		Map<String, Long> avant = instantane(demo);
		// Le dernier geste du semis (les vaccinations) échouera : l'effacement, lui,
		// aura déjà eu lieu dans la transaction.
		proprietaire.execute("ALTER TABLE vaccination ADD CONSTRAINT zz_refus CHECK (false) NOT VALID");
		try {
			assertThatThrownBy(this::lancer).isInstanceOf(RuntimeException.class);
		}
		finally {
			proprietaire.execute("ALTER TABLE vaccination DROP CONSTRAINT zz_refus");
		}

		assertThat(instantane(demo)).isEqualTo(avant);
		assertThat(existe("ticket", marqueur.ticket())).isTrue();
	}

	@Test
	@DisplayName("sans clinique « demo », elle ne crée rien et ne plante pas : l'amorce l'installe")
	void cliniqueAbsente() {
		var collecteur = ecouterLeJournal();
		try {
			lancer();
			assertThat(messages(collecteur)).anyMatch(m -> m.contains("n'existe pas"));
		}
		finally {
			cesserDEcouter(collecteur);
		}
		assertThat(proprietaire.queryForObject("SELECT count(*) FROM tenant", Long.class))
				.isZero();
	}
}
