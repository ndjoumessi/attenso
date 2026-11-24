package ai.attenso.courriel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envoi par SMTP, et rien d'autre.
 *
 * <p>SMTP plutôt que l'API d'un prestataire : le choix du serveur d'envoi
 * reste à l'exploitant, qui peut en prendre un européen sans que ce dépôt ait à
 * le connaître. La politique de confidentialité promet qu'aucun prestataire
 * hors Union européenne n'est appelé pour faire fonctionner le service ; lier
 * le code à une API américaine l'aurait démentie.
 *
 * <p>Éteint par défaut, comme le push et la souscription : une installation
 * sans courriel fonctionne, elle perd seulement la récupération de mot de
 * passe — et le démarrage le dit.
 */
@Component
public class CourrielSmtp implements ServiceCourriel {

	private static final Logger JOURNAL = LoggerFactory.getLogger(CourrielSmtp.class);

	private final JavaMailSender poste;

	private final ProprietesCourriel proprietes;

	public CourrielSmtp(JavaMailSender poste, ProprietesCourriel proprietes) {
		this.poste = poste;
		this.proprietes = proprietes;
		if (proprietes.actif()) {
			JOURNAL.info("envoi de courriels actif, expéditeur {}", proprietes.expediteur());
		}
		else {
			JOURNAL.info("envoi de courriels inactif : la récupération de mot de passe "
					+ "sera refusée, le reste du service fonctionne");
		}
	}

	@Override
	public boolean envoyer(String destinataire, String sujet, String corps) {
		if (!proprietes.actif()) {
			return false;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(proprietes.expediteur());
		message.setTo(destinataire);
		message.setSubject(sujet);
		message.setText(corps);
		try {
			poste.send(message);
			return true;
		}
		catch (MailException panne) {
			// Le destinataire n'apparaît PAS dans le journal : c'est l'adresse
			// d'un client, et un journal d'erreurs finit souvent ailleurs que
			// sur la machine qui l'a écrit.
			JOURNAL.error("envoi de courriel en échec : {}", panne.getMessage());
			return false;
		}
	}

	@Override
	public boolean actif() {
		return proprietes.actif();
	}
}
