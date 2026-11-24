package ai.attenso.supervision;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supervision et alerte (EF-903).
 *
 * @param webhook URL à laquelle poster l'alerte (Slack, Mattermost, n'importe
 *        quel récepteur). Vide = journalisation seule, ce qui reste exploitable
 *        par un agrégateur de journaux. L'alerte ne doit dépendre d'aucun
 *        service tiers obligatoire.
 * @param smsEnAttenteMax  au-delà, la file d'envoi est considérée bloquée : le
 *        produit promet un SMS, ne pas le voir partir est une panne même si
 *        l'application répond.
 */
@ConfigurationProperties("attenso.supervision")
public record ProprietesSupervision(String webhook, int smsEnAttenteMax,
		Duration tickToleranceMax, Duration delaiAppel) {

	public ProprietesSupervision {
		smsEnAttenteMax = smsEnAttenteMax <= 0 ? 50 : smsEnAttenteMax;
		tickToleranceMax = tickToleranceMax == null ? Duration.ofMinutes(5) : tickToleranceMax;
		delaiAppel = delaiAppel == null ? Duration.ofSeconds(5) : delaiAppel;
	}

	public boolean alerteExterne() {
		return webhook != null && !webhook.isBlank();
	}
}
