package ai.attenso.utilisateur;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

	Optional<Utilisateur> findByTenantIdAndEmailAndActifTrue(UUID tenantId, String email);
}
