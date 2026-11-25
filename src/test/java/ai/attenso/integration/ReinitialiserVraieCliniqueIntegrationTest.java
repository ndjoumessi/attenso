package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.amorce.AmorceClinique;
import ai.attenso.amorce.ReinitialisationDemonstration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * LE cas qui protège une vraie clinique : les deux premiers verrous sont
 * levés, la configuration désigne « cerisiers », et cerisiers existe, avec
 * ses tickets, ses animaux et son compte.
 */
@TestPropertySource(properties = {
		"attenso.amorce.active=true",
		"attenso.amorce.demonstration=true",
		"attenso.amorce.reinitialiser=true",
		"attenso.amorce.slug=cerisiers",
		"attenso.amorce.nom=Clinique des Cerisiers",
		"attenso.amorce.email=accueil@cerisiers.test",
		"attenso.amorce.mot-de-passe=mdp-de-cerisiers-de-test",
		"attenso.amorce.delai-grace-secondes=900",
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.minimum-idle=1",
})
class ReinitialiserVraieCliniqueIntegrationTest extends ReinitialisationBase {

	@Autowired
	ApplicationContext conteneur;

	@Autowired
	ReinitialisationDemonstration reinitialisation;

	@Test
	@DisplayName("un slug qui n'est pas celui de la démonstration : elle refuse, la vraie clinique reste intacte")
	void refuse() {
		assertThat(conteneur.getBeanNamesForType(ReinitialisationDemonstration.class))
				.as("la commande est bien là").hasSize(1);
		UUID cerisiers = creerClinique("cerisiers", "Clinique des Cerisiers", 2, 180);
		ReinitialisationBase.Marqueur marqueur = chargerUneClinique(cerisiers);
		UUID demo = creerClinique("demo", "Clinique de démonstration", 2, 180);
		chargerUneClinique(demo);
		Map<String, Long> avantCerisiers = instantane(cerisiers);
		Map<String, Long> avantDemo = instantane(demo);

		var collecteur = ecouterLeJournal();
		try {
			reinitialisation.run(new DefaultApplicationArguments(new String[0]));
			assertThat(messages(collecteur)).anyMatch(
					m -> m.contains("REFUSÉE") && m.contains("cerisiers"));
		}
		finally {
			cesserDEcouter(collecteur);
		}

		assertThat(instantane(cerisiers)).isEqualTo(avantCerisiers);
		assertThat(existe("ticket", marqueur.ticket())).isTrue();
		assertThat(existe("animal", marqueur.animal())).isTrue();
		assertThat(existe("utilisateur", marqueur.utilisateur())).isTrue();
		// La clinique de démonstration n'était pas visée non plus : un refus ne vide rien.
		assertThat(instantane(demo)).isEqualTo(avantDemo);
		// Un refus ne touche à RIEN, la grâce non plus (900 est configuré, 180 reste).
		assertThat(graceDe(cerisiers)).as("la grâce de la vraie clinique").isEqualTo(180);
		assertThat(graceDe(demo)).as("la grâce de la démonstration, non visée").isEqualTo(180);
	}
}
