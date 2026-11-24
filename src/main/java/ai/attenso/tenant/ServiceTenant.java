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

	/**
	 * Résout la clinique depuis le jeton d'appairage de son écran (EF-601).
	 *
	 * <p>L'erreur rendue est la même que pour un slug inconnu — un 404 — et
	 * c'est voulu : distinguer « jeton faux » de « clinique sans écran »
	 * dirait à qui sonde laquelle des deux il vient de toucher.
	 */
	@Transactional(readOnly = true)
	public Tenant parJetonEcran(String jeton) {
		if (jeton == null || jeton.isBlank()) {
			throw new TenantInconnu("écran");
		}
		return tenants.findByJetonEcranAndActifTrue(jeton)
				.orElseThrow(() -> new TenantInconnu("écran"));
	}

	@Transactional(readOnly = true)
	public Tenant parId(UUID id) {
		return tenants.findById(id)
				.filter(Tenant::actif)
				.orElseThrow(() -> new TenantInconnu(id.toString()));
	}
}
