package ai.attenso.tenant;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Résolution de la clinique. La table {@code tenant} est volontairement hors
 * RLS : il faut pouvoir résoudre le slug d'un QR code avant de savoir de quel
 * tenant il s'agit.
 */
@Service
public class ServiceTenant {

	private final TenantRepository tenants;

	public ServiceTenant(TenantRepository tenants) {
		this.tenants = tenants;
	}

	@Transactional(readOnly = true)
	public Tenant parSlug(String slug) {
		return tenants.findBySlugAndActifTrue(slug).orElseThrow(() -> new TenantInconnu(slug));
	}

	@Transactional(readOnly = true)
	public Tenant parId(UUID id) {
		return tenants.findById(id)
				.filter(Tenant::actif)
				.orElseThrow(() -> new TenantInconnu(id.toString()));
	}
}
