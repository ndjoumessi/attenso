package ai.attenso.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param verificateur {@code format} (défaut) ou {@code octopush}.
 * @param delaiLecture délai court et assumé : la vérification est sur le chemin
 *        critique de quelqu'un debout au comptoir. Passé ce délai, on laisse
 *        entrer sans savoir, plutôt que de faire attendre.
 * @param coutCentimes coût d'une vérification, pour le journal. Le volume se
 *        déduit du nombre de tickets à numéro nouveau.
 */
@ConfigurationProperties("attenso.hlr")
public record ProprietesHlr(String verificateur, Duration delaiLecture,
		java.math.BigDecimal coutCentimes) {
}
