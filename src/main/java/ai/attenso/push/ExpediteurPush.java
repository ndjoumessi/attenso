package ai.attenso.push;

import java.security.Security;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Envoi d'une notification poussée (EF-307).
 *
 * <p>Le contenu est chiffré de bout en bout pour l'abonnement destinataire :
 * l'opérateur de push (Google, Apple, Mozilla) relaie un bloc qu'il ne peut
 * pas lire. C'est ce qui rend acceptable d'y mettre le numéro de ticket.
 *
 * <p>Ce composant n'existe que si les clés VAPID sont configurées. Sans elles,
 * aucun abonnement n'est proposé au client et tout part en SMS.
 */
@Component
@ConditionalOnProperty(name = "attenso.push.cle-publique")
public class ExpediteurPush {

	/** Codes signifiant « cet abonnement est mort », d'après la spécification. */
	private static final int PARTI = 404;

	private static final int EXPIRE = 410;

	private static final Logger JOURNAL = LoggerFactory.getLogger(ExpediteurPush.class);

	/** {@code null} quand les clés sont mal formées : le push est alors éteint. */
	private final PushService service;

	/**
	 * <strong>Ne lève jamais.</strong> Une clé VAPID mal recopiée est une
	 * faute de configuration banale ; la laisser remonter ferait échouer le
	 * démarrage de l'application, donc empêcherait la clinique d'ouvrir sa
	 * salle d'attente le matin à cause d'une fonction d'agrément. Le push
	 * s'éteint, l'erreur est journalisée nommément, et tout repart en SMS.
	 */
	public ExpediteurPush(ProprietesPush proprietes) {
		// Deux états bien distincts, et les confondre serait une faute de
		// journalisation : des clés ABSENTES sont le cas normal d'une clinique
		// qui n'utilise pas le push — ça se dit une fois, en information. Des
		// clés PRÉSENTES mais illisibles sont une faute de configuration —
		// ça, c'est une erreur. Crier au loup à chaque démarrage apprendrait à
		// l'exploitant à ignorer les ERROR, ce qui ruine la supervision.
		if (!proprietes.actif()) {
			JOURNAL.info("aucune clé VAPID configurée : les notifications poussées sont "
					+ "inactives, tout part en SMS");
			this.service = null;
			return;
		}
		// Les courbes elliptiques de VAPID ne sont pas dans le fournisseur JCE
		// par défaut de tous les JDK : la bibliothèque exige BouncyCastle.
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
		PushService construit = null;
		try {
			construit = new PushService(proprietes.clePublique(), proprietes.clePrivee(),
					proprietes.sujet());
		}
		catch (Exception malForme) {
			JOURNAL.error("clés VAPID inutilisables ({}) : les notifications poussées sont "
					+ "DÉSACTIVÉES, tout repartira en SMS. La file, elle, fonctionne.",
					malForme.getMessage());
		}
		this.service = construit;
	}

	public boolean actif() {
		return service != null;
	}

	/**
	 * @return {@code true} si le message est parti, {@code false} s'il faut
	 *         retomber sur le SMS. Un abonnement mort est signalé par
	 *         {@code ResultatPush.abonnementMort()} pour que l'appelant le
	 *         retire au lieu de le réessayer sans fin.
	 */
	public ResultatPush envoyer(AbonnementPush abonnement, String corps) {
		if (service == null) {
			return ResultatPush.echec("push mal configuré sur ce service");
		}
		try {
			Notification notification = new Notification(abonnement.endpoint(),
					abonnement.clePublique(), abonnement.cleAuth(),
					corps.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			int statut = service.send(notification).getStatusLine().getStatusCode();
			if (statut == PARTI || statut == EXPIRE) {
				return ResultatPush.mort();
			}
			if (statut >= 200 && statut < 300) {
				return ResultatPush.succes();
			}
			return ResultatPush.echec("push refusé : HTTP " + statut);
		}
		catch (Exception panne) {
			JOURNAL.warn("envoi push en échec : {}", panne.getMessage());
			return ResultatPush.echec(panne.getClass().getSimpleName() + " : "
					+ panne.getMessage());
		}
	}
}
