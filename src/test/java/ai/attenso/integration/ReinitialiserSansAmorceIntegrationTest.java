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

/** reinitialiser et demonstration vrais, mais active faux : rien ne pourrait ressemer. */
@TestPropertySource(properties = {
		"attenso.amorce.active=false",
		"attenso.amorce.demonstration=true",
		"attenso.amorce.reinitialiser=true",
		"attenso.amorce.slug=demo",
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.minimum-idle=1",
})
class ReinitialiserSansAmorceIntegrationTest extends ReinitialisationBase {

	@Autowired
	ApplicationContext conteneur;

	@Autowired
	ReinitialisationDemonstration reinitialisation;

	@Test
	@DisplayName("sans amorce active, elle refuse au lieu d'effacer sans pouvoir ressemer")
	void refuse() {
		assertThat(conteneur.getBeanNamesForType(AmorceClinique.class))
				.as("l'amorce n'existe pas dans ce conteneur").isEmpty();
		assertThat(conteneur.getBeanNamesForType(ReinitialisationDemonstration.class))
				.as("la commande est bien là").hasSize(1);
		UUID demo = creerClinique("demo", "Clinique de démonstration", 2, 180);
		chargerUneClinique(demo);
		Map<String, Long> avant = instantane(demo);

		var collecteur = ecouterLeJournal();
		try {
			reinitialisation.run(new DefaultApplicationArguments(new String[0]));
			assertThat(messages(collecteur)).anyMatch(
					m -> m.contains("REFUSÉE") && m.contains("active"));
		}
		finally {
			cesserDEcouter(collecteur);
		}

		assertThat(instantane(demo)).isEqualTo(avant);
	}
}
