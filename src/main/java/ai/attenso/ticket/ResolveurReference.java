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
		return jdbc.query("SELECT attenso_tenant_de_reference(?)",
				ligne -> ligne.next() ? Optional.ofNullable(ligne.getObject(1, UUID.class))
						: Optional.<UUID>empty(),
				reference);
	}
}
