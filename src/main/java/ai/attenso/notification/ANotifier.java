package ai.attenso.notification;

import java.util.UUID;

/**
 * Une ligne de la file d'envoi.
 *
 * @param canal    SMS ou PUSH. Décidé à la DEMANDE et non à l'envoi : le
 *                 client peut s'abonner ou se désabonner entre les deux, et
 *                 une notification doit partir par le canal sur lequel son
 *                 coût a été estimé.
 * @param ticketId sert à retrouver l'abonnement push, et à le retirer s'il
 *                 est mort.
 */
public record ANotifier(UUID id, UUID ticketId, Canal canal, String destinataire, String corps,
		int tentatives) {
}
