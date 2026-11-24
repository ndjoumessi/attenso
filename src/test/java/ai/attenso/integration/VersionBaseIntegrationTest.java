package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.supervision.VeilleVersionBase;
import ai.attenso.supervision.VeilleVersionBase.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * La veille a bien tourné au démarrage, sur la vraie base.
 *
 * <p>Tester la fonction de constat seule ne prouverait pas qu'elle est branchée.
 * Ici elle a interrogé le conteneur, sous le rôle applicatif soumis au RLS —
 * ce qui vérifie au passage que {@code SHOW server_version} passe sous ce rôle.
 */
class VersionBaseIntegrationTest extends SocleIntegration {

	@Autowired
	private VeilleVersionBase veille;

	@Test
	@DisplayName("a lu la version de la base et constaté la concordance")
	void aConstateLaConcordance() {
		assertThat(veille.verdict())
				.describedAs("la veille n'a pas tourné au démarrage")
				.isEqualTo(Verdict.CONCORDE);
		assertThat(veille.versionLue()).isNotBlank();
	}
}
