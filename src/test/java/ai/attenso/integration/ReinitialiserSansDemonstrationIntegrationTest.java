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

/** reinitialiser vrai, demonstration faux : le deuxième verrou. */
@TestPropertySource(properties = {
		"attenso.amorce.active=true",
		"attenso.amorce.demonstration=false",
		"attenso.amorce.reinitialiser=true",
		"attenso.amorce.slug=demo",
		"attenso.amorce.nom=Clinique de démonstration",
		"attenso.amorce.email=accueil@demo.attenso.test",
		"attenso.amorce.mot-de-passe=mdp-de-demonstration-de-test",
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.minimum-idle=1",
})
class ReinitialiserSansDemonstrationIntegrationTest extends ReinitialisationBase {

	@Autowired
	ApplicationContext conteneur;

	@Autowired
	ReinitialisationDemonstration reinitialisation;

	@Test
	@DisplayName("reinitialiser vrai mais demonstration faux : elle refuse, et la clinique reste intacte")
	void refuse() {
		assertThat(conteneur.getBeanNamesForType(ReinitialisationDemonstration.class))
				.as("la commande est bien là").hasSize(1);
		UUID demo = creerClinique("demo", "Clinique de démonstration", 2, 180);
		chargerUneClinique(demo);
		Map<String, Long> avant = instantane(demo);

		var collecteur = ecouterLeJournal();
		try {
			reinitialisation.run(new DefaultApplicationArguments(new String[0]));
			assertThat(messages(collecteur)).anyMatch(
					m -> m.contains("REFUSÉE") && m.contains("demonstration"));
		}
		finally {
			cesserDEcouter(collecteur);
		}

		assertThat(instantane(demo)).isEqualTo(avant);
	}
}
