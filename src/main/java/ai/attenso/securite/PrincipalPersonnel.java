package ai.attenso.securite;

import ai.attenso.utilisateur.Role;
import java.util.UUID;

public record PrincipalPersonnel(UUID utilisateurId, UUID tenantId, Role role) {
}
