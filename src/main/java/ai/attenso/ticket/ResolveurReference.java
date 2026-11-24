package ai.attenso.ticket;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Trouve le tenant d'une référence de lien SMS, pour pouvoir le lier avant
 * toute lecture cloisonnée. Passe par la fonction {@code SECURITY DEFINER} de
 * {@code V2__rls.sql} : c'est le seul chemin de lecture hors RLS, et il ne
 * rend qu'un identifiant de clinique.
 */
@Component
public class ResolveurReference {

	private final JdbcTemplate jdbc;

	public ResolveurReference(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<UUID> tenantDe(String reference) {
		return parFonction("SELECT attenso_tenant_de_reference(?)", reference);
	}

	/** Le pendant pour une référence de rendez-vous (EF-905). */
	public Optional<UUID> tenantDeRendezVous(String reference) {
		return parFonction("SELECT attenso_tenant_de_reference_rdv(?)", reference);
	}

	private Optional<UUID> parFonction(String requete, String reference) {
		return jdbc.query(requete,
				ligne -> ligne.next() ? Optional.ofNullable(ligne.getObject(1, UUID.class))
						: Optional.<UUID>empty(),
				reference);
	}
}
