package ai.attenso.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	Optional<Tenant> findBySlugAndActifTrue(String slug);

	/**
	 * Sans le filtre « actif », et c'est nécessaire : une clinique inscrite
	 * mais pas encore payée RÉSERVE son adresse. La chercher en « actif
	 * seulement » la rendrait invisible, et la clinique suivante du même nom
	 * recevrait le même slug — que l'index unique refuserait, au milieu de son
	 * inscription.
	 */
	boolean existsBySlug(String slug);

	Optional<Tenant> findByJetonEcranAndActifTrue(String jetonEcran);

	List<Tenant> findAllByActifTrue();
}
