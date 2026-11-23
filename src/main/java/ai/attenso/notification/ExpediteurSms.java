package ai.attenso.notification;

/**
 * L'envoi effectif. Une seule implémentation en V0 — celle qui journalise —
 * parce que l'adaptateur de l'agrégateur attend deux décisions qui ne sont pas
 * encore prises : quel nom d'expéditeur affiche le SMS, et sur quel compte.
 * Voir docs/note-canal-sms.md.
 */
public interface ExpediteurSms {

	ResultatEnvoi envoyer(String destinataire, String corps);
}
