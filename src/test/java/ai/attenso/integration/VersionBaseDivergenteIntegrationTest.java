package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.supervision.VeilleVersionBase;
import ai.attenso.supervision.VeilleVersionBase.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Le cas qui justifie la veille : la base n'est pas la majeure attendue.
 *
 * <p>On annonce une majeure fausse plutôt que de démarrer un second conteneur
 * dans une autre version — le constat est le même, et cela prouve ce qu'on veut
 * prouver : que la veille interroge réellement la base et compare réellement à
 * la valeur configurée. Sans ce test, rien ne distinguerait une garde branchée
 * d'une garde qui renvoie toujours CONCORDE.
 *
 * <p>Ce contexte Spring est à lui seul (la propriété diffère), ce qui coûte un
 * démarrage. C'est le prix d'une garde dont on sait qu'elle se déclenche.
 */
@TestPropertySource(properties = "attenso.base.majeure-attendue=11")
class VersionBaseDivergenteIntegrationTest extends SocleIntegration {

	@Autowired
	private VeilleVersionBase veille;

	@Test
	@DisplayName("constate la divergence, et ne refuse pas de démarrer pour autant")
	void constateLaDivergence() {
		assertThat(veille.verdict()).isEqualTo(Verdict.DIVERGE);
		// Le contexte a démarré : la garde avertit, elle n'empêche pas. Faire
		// tomber le service d'une clinique pour un écart de version serait une
		// panne provoquée par la garde elle-même.
		assertThat(veille.versionLue()).isNotBlank();
	}
}
