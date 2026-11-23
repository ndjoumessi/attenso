package ai.attenso.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat ne connaît pas {@code .webmanifest} et le sert en
 * {@code application/octet-stream} — auquel cas le navigateur ignore le
 * manifeste et l'application ne peut pas être ajoutée à l'écran d'accueil.
 * Or sur iOS, c'est l'ajout à l'écran d'accueil qui conditionne le push web :
 * sans ça, il ne reste que le SMS, qui coûte.
 */
@Configuration
public class ConfigurationTypesMime {

	@Bean
	WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> typesMime() {
		return usine -> {
			MimeMappings types = new MimeMappings(MimeMappings.DEFAULT);
			types.add("webmanifest", "application/manifest+json");
			usine.setMimeMappings(types);
		};
	}
}
