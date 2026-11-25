package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.amorce.AmorceClinique;
import ai.attenso.amorce.ProprietesAmorce;
import ai.attenso.amorce.ReinitialisationDemonstration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La grâce configurée est posée sur la clinique AVANT le semis, pour que le
 * ticket appelé de la démonstration en tire son échéance. Le premier test passe
 * par le bean de Spring (propriété lue de l'environnement) ; les valeurs hors
 * bande montent la commande à la main sur le même conteneur, pour ne pas payer
 * un contexte de plus par valeur.
 */
@TestPropertySource(properties = {
		"attenso.amorce.active=true",
		"attenso.amorce.demonstration=true",
		"attenso.amorce.reinitialiser=true",
		"attenso.amorce.slug=demo",
		"attenso.amorce.nom=Clinique de démonstration",
		"attenso.amorce.email=accueil@demo.attenso.test",
		"attenso.amorce.mot-de-passe=mdp-de-demonstration-de-test",
		"attenso.amorce.delai-grace-secondes=900",
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.minimum-idle=1",
})
class ReinitialiserGraceIntegrationTest extends ReinitialisationBase {

	private static final DefaultApplicationArguments SANS_ARGUMENT =
			new DefaultApplicationArguments(new String[0]);

	@Autowired
	ReinitialisationDemonstration reinitialisation;

	@Autowired
	ObjectProvider<AmorceClinique> amorce;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcTemplate jdbc;

	private ReinitialisationDemonstration avecLaGrace(Integer secondes) {
		ProprietesAmorce p = new ProprietesAmorce(true, true, "demo", "Clinique de démonstration",
				"accueil@demo.attenso.test", "mdp-de-demonstration-de-test", null, secondes, null,
				false);
		return new ReinitialisationDemonstration(p, jdbc, amorce, transaction);
	}

	@Test
	@DisplayName("la grâce configurée est posée sur la clinique, et le ticket appelé en hérite")
	void appliqueEtHerite() {
		UUID demo = creerClinique("demo", "Clinique de démonstration", 2, 180);
		chargerUneClinique(demo);

		var collecteur = ecouterLeJournal();
		try {
			reinitialisation.run(SANS_ARGUMENT);
			assertThat(messages(collecteur)).anyMatch(m -> m.contains("900"));
		}
		finally {
			cesserDEcouter(collecteur);
		}

		assertThat(graceDe(demo)).as("la grâce de la clinique").isEqualTo(900);
		// Le cœur du correctif : l'échéance du ticket appelé suit la NOUVELLE grâce.
		// Si la mise à jour passait après le semis, ce serait 180.
		assertThat(graceDuTicketAppele(demo)).isEqualTo(900);
	}

	@Test
	@DisplayName("une valeur hors bande est bornée à 3600, la transaction aboutit et le journal le dit")
	void borneLeMaximum() {
		UUID demo = creerClinique("demo", "Clinique de démonstration", 2, 180);
		chargerUneClinique(demo);

		var collecteur = ecouterLeJournal();
		try {
			avecLaGrace(99_999).run(SANS_ARGUMENT);
			assertThat(messages(collecteur)).anyMatch(m -> m.contains("99999")
					&& m.contains("3600") && m.contains("contrainte"));
		}
		finally {
			cesserDEcouter(collecteur);
		}

		assertThat(graceDe(demo)).isEqualTo(3600);
		assertThat(graceDuTicketAppele(demo)).isEqualTo(3600);
		assertThat(compter("ticket", demo)).as("la démonstration est bien semée").isEqualTo(19);
	}

	@Test
	@DisplayName("une valeur négative est bornée à 0")
	void borneLeMinimum() {
		UUID demo = creerClinique("demo", "Clinique de démonstration", 2, 180);

		avecLaGrace(-5).run(SANS_ARGUMENT);

		assertThat(graceDe(demo)).isZero();
	}
}
