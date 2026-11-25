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

/** La configuration de la démonstration sur Railway : installée, jamais réinitialisée. */
@TestPropertySource(properties = {
		"attenso.amorce.active=true",
		"attenso.amorce.demonstration=true",
		"attenso.amorce.slug=demo",
		"attenso.amorce.nom=Clinique de démonstration",
		"attenso.amorce.email=accueil@demo.attenso.test",
		"attenso.amorce.mot-de-passe=mdp-de-demonstration-de-test",
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.minimum-idle=1",
})
class ReinitialiserParDefautIntegrationTest extends ReinitialisationBase {

	@Autowired
	ApplicationContext conteneur;

	@Test
	@DisplayName("sans attenso.amorce.reinitialiser, la commande n'existe pas dans le conteneur")
	void laCommandeNExistePas() {
		assertThat(conteneur.getBeanNamesForType(ReinitialisationDemonstration.class))
				.isEmpty();
		// Le reste de l'amorce, lui, est bien là : c'est la propriété seule qui manque.
		assertThat(conteneur.getBeanNamesForType(AmorceClinique.class)).hasSize(1);
	}
}
