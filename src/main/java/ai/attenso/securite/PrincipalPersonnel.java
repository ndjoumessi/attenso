package ai.attenso.securite;

import ai.attenso.utilisateur.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * @param emisLe l'instant d'émission du jeton. Porté jusqu'ici parce qu'un
 *        jeton plus ancien que le dernier changement de mot de passe doit être
 *        refusé — sans quoi changer son mot de passe ne ferme aucune session.
 */
public record PrincipalPersonnel(UUID utilisateurId, UUID tenantId, Role role,
		Instant emisLe) {
}
