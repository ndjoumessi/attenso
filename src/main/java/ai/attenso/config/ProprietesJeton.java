package ai.attenso.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Secret de signature des jetons. Volontairement sans valeur par défaut : une
 * application qui démarre avec un secret vide signe des jetons forgeables, et
 * le défaut serait le même sur toutes les installations.
 */
@Validated
@ConfigurationProperties("attenso.jeton")
public record ProprietesJeton(
		@NotBlank(message = "ATTENSO_JWT_SECRET est obligatoire (openssl rand -base64 48)")
		String secret,
		Duration dureeTicket,
		Duration dureePersonnel) {
}
