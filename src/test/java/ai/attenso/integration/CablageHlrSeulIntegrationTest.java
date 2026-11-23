package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.notification.ExpediteurSms;
import ai.attenso.notification.ExpediteurSmsJournal;
import ai.attenso.numero.VerificateurNumero;
import ai.attenso.numero.VerificateurNumeroOctopush;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Le HLR activé seul, sans envoi réel de SMS.
 *
 * <p>Combinaison utile en vrai — mesurer la qualité des numéros d'une clinique
 * avant d'ouvrir les SMS — et c'est exactement celle qui empêchait le démarrage
 * tant que le client HTTP n'était conditionné qu'au réglage des SMS.
 */
@TestPropertySource(properties = {
		"attenso.sms.expediteur=journal",
		"attenso.hlr.verificateur=octopush",
		"attenso.octopush.login=compte@clinique.test",
		"attenso.octopush.cle=cle-de-test",
})
class CablageHlrSeulIntegrationTest extends SocleIntegration {

	@Autowired
	VerificateurNumero verificateur;

	@Autowired
	ExpediteurSms expediteur;

	@Test
	@DisplayName("l'application démarre avec le HLR actif et les SMS en journal")
	void lesDeuxReglagesSontIndependants() {
		assertThat(verificateur).isInstanceOf(VerificateurNumeroOctopush.class);
		assertThat(expediteur)
				.as("aucun SMS réel ne part pour autant")
				.isInstanceOf(ExpediteurSmsJournal.class);
	}
}
