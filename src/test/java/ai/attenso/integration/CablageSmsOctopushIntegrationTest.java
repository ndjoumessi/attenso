package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.notification.ExpediteurSms;
import ai.attenso.notification.ExpediteurSmsOctopush;
import ai.attenso.numero.VerificateurNumero;
import ai.attenso.numero.VerificateurNumeroFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * L'envoi réel activé, sans HLR.
 *
 * <p>Ce test existe parce que son absence a laissé passer un adaptateur qui ne
 * démarrait pas : il dépendait d'un {@code RestClient.Builder} auto-configuré
 * qui n'existe pas dans cette application. Les six tests de l'adaptateur
 * construisaient leur propre client et ne pouvaient donc pas s'en apercevoir —
 * tester la logique ne teste pas le câblage.
 */
@TestPropertySource(properties = {
		"attenso.sms.expediteur=octopush",
		"attenso.hlr.verificateur=format",
		"attenso.octopush.login=compte@clinique.test",
		"attenso.octopush.cle=cle-de-test",
})
class CablageSmsOctopushIntegrationTest extends SocleIntegration {

	@Autowired
	ExpediteurSms expediteur;

	@Autowired
	VerificateurNumero verificateur;

	@Test
	@DisplayName("l'application démarre avec l'envoi Octopush et sans HLR")
	void lApplicationDemarreAvecLEnvoiReel() {
		assertThat(expediteur).isInstanceOf(ExpediteurSmsOctopush.class);
		assertThat(verificateur).isInstanceOf(VerificateurNumeroFormat.class);
	}
}
