package ai.attenso.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Accès à l'agrégateur.
 *
 * <p>Pas de validation déclarative ici, et c'est délibéré : ces propriétés sont
 * chargées même quand Octopush n'est pas utilisé, où des identifiants vides
 * sont parfaitement normaux. L'exigence est portée par
 * {@link ConfigurationOctopush}, qui n'existe que si l'un des deux usages est
 * actif — et qui peut dire précisément laquelle des deux variables manque.
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
@ConfigurationProperties("attenso.octopush")
public record ProprietesOctopush(String url, String login, String cle, String type,
		String purpose, boolean simulation, Duration delaiConnexion, Duration delaiLecture) {
}
