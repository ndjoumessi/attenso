package ai.attenso.notification;

import java.util.UUID;

/**
 * Un SMS à expédier.
 *
 * @param reference identifiant de la notification. Transmis à l'agrégateur
 *        comme clé d'idempotence : sans elle, une coupure survenant APRÈS que
 *        l'agrégateur a accepté le message ferait repartir le réessai — et
 *        payer le SMS une seconde fois.
 * @param expediteur nom affiché, celui de la clinique.
 */
public record MessageSms(UUID reference, String destinataire, String expediteur, String corps) {
}
