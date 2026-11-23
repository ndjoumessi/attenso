package ai.attenso.notification;

import ai.attenso.config.ProprietesSms;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Expéditeur de développement : rien ne part, tout est journalisé, et le coût
 * est calculé pour de vrai. C'est volontaire — la colonne
 * {@code cout_centimes} doit être exploitable dès le premier jour du pilote,
 * sinon la marge ne se mesure jamais.
 */
@Component
@ConditionalOnProperty(name = "attenso.sms.expediteur", havingValue = "journal",
		matchIfMissing = true)
public class ExpediteurSmsJournal implements ExpediteurSms {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ExpediteurSmsJournal.class);

	private final ProprietesSms proprietes;

	public ExpediteurSmsJournal(ProprietesSms proprietes) {
		this.proprietes = proprietes;
	}

	@Override
	public ResultatEnvoi envoyer(MessageSms message) {
		int segments = CompteurSegments.segments(message.corps());
		BigDecimal cout = proprietes.coutCentimesParSegment()
				.multiply(BigDecimal.valueOf(segments));
		JOURNAL.info("SMS [journal] de « {} » vers {} — {} segment(s), {} centimes, "
				+ "{} caractères : {}", message.expediteur(), masquer(message.destinataire()),
				segments, cout, message.corps().length(), message.corps());
		return ResultatEnvoi.reussi("journal-" + UUID.randomUUID(), cout, null);
	}

	/** Le numéro complet n'a rien à faire dans un journal applicatif. */
	private String masquer(String numero) {
		return numero.length() <= 4 ? "****" : "****" + numero.substring(numero.length() - 4);
	}
}
