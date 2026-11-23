package ai.attenso.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Accès à l'agrégateur.
 *
 * <p>Identifiants sans valeur par défaut et exigés au démarrage : une
 * application qui démarre avec une clé vide échouerait silencieusement sur
 * chaque envoi, et le premier à s'en apercevoir serait un client qui n'a pas
 * reçu son « revenez ».
 *
 * @param type        {@code sms_premium} route en garanti et autorise un
 *                    expéditeur alphanumérique personnalisé, ce dont tout ce
 *                    montage dépend.
 * @param purpose     {@code alert} = transactionnel. Pas de mention STOP, pas
 *                    de restriction horaire — ce qui serait faux et coûteux
 *                    pour du marketing, et juste ici.
 * @param simulation  mode bac à sable de l'API : la requête est validée mais
 *                    rien n'est envoyé ni facturé. Sert à la répétition avant
 *                    le pilote.
 */
@Validated
@ConfigurationProperties("attenso.octopush")
@ConditionalOnProperty(name = "attenso.sms.expediteur", havingValue = "octopush")
public record ProprietesOctopush(
		String url,
		@NotBlank(message = "ATTENSO_OCTOPUSH_LOGIN est obligatoire") String login,
		@NotBlank(message = "ATTENSO_OCTOPUSH_CLE est obligatoire") String cle,
		String type,
		String purpose,
		boolean simulation,
		Duration delaiConnexion,
		Duration delaiLecture) {
}
