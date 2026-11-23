package ai.attenso.notification;

/**
 * L'envoi effectif. Deux implémentations : {@link ExpediteurSmsJournal} en
 * développement, qui ne fait que journaliser et calculer le coût, et
 * {@code ExpediteurSmsOctopush} en production.
 */
public interface ExpediteurSms {

	ResultatEnvoi envoyer(MessageSms message);
}
