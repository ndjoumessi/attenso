package ai.attenso.notification;

import java.util.UUID;

/** Une ligne de la file d'envoi. */
public record ANotifier(UUID id, String destinataire, String corps, int tentatives) {
}
