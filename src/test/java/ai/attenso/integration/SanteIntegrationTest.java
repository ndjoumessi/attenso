package ai.attenso.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Le contrôle de vivacité, celui que le conteneur interroge.
 *
 * <p>Ce test existe pour une raison précise : {@code spring-boot-starter-mail}
 * installe de lui-même un indicateur de santé qui essaie de joindre le serveur
 * SMTP. Sans SMTP configuré — l'état d'une installation neuve, et l'état
 * documenté de ce produit — il faisait passer {@code /actuator/health} à DOWN,
 * donc le conteneur en « unhealthy », alors que la file, les SMS et les écrans
 * tournaient. Une ligne de configuration le coupe ; ce test est ce qui
 * empêchera de la retirer par mégarde.
 */
@AutoConfigureMockMvc
class SanteIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Test
	@DisplayName("le service est UP sans serveur de courriel configuré")
	void leServiceEstUpSansCourriel() throws Exception {
		// Le socle ne configure aucun SMTP, exactement comme une machine
		// fraîchement installée.
		http.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"UP\"")));
	}
}
