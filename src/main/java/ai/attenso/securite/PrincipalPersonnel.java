package ai.attenso.securite;

import ai.attenso.utilisateur.Role;
import java.util.UUID;

/**
 * @param jetonVersion la version portée par le jeton. Comparée à celle du
 *        compte à chaque requête : un jeton qui ne porte plus la bonne est
 *        refusé, et c'est ainsi que changer son mot de passe ferme les
 *        sessions déjà ouvertes.
 */
public record PrincipalPersonnel(UUID utilisateurId, UUID tenantId, Role role,
		int jetonVersion) {
}
