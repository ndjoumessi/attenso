package ai.attenso.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param verificateur {@code format} (défaut) ou {@code octopush}.
 * @param delaiLecture délai court et assumé : la vérification est sur le chemin
 *        critique de quelqu'un debout au comptoir. Passé ce délai, on laisse
 *        entrer sans savoir, plutôt que de faire attendre.
 * @param coutCentimes coût d'une vérification, pour le journal.
 * @param maxParJour   budget d'appels payants par clinique et par jour.
 *        <strong>Compté sur les vérifications et non sur les tickets</strong> :
 *        un numéro refusé ne crée pas de ticket, donc un plafond de tickets ne
 *        plafonne rien dans le seul cas qui coûte. Au-delà, on rend
 *        « indéterminé » sans appeler — le ticket part quand même.
 */
@ConfigurationProperties("attenso.hlr")
public record ProprietesHlr(String verificateur, Duration delaiLecture,
		java.math.BigDecimal coutCentimes, int maxParJour) {
}
