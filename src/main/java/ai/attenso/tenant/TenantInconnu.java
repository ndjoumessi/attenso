package ai.attenso.tenant;

/** Slug inconnu ou clinique désactivée. Se traduit en HTTP 404. */
public class TenantInconnu extends RuntimeException {

	public TenantInconnu(String slug) {
		super("clinique inconnue : " + slug);
	}
}
