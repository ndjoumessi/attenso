package ai.attenso.journal;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Journal d'accès, en ajout seul. Pas d'entité : rien ne le modifie jamais. */
@Repository
public class JournalDao {

	private final JdbcTemplate jdbc;

	public JournalDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void consigner(UUID tenantId, UUID utilisateurId, UUID ticketId, String action,
			String ip) {
		jdbc.update("""
				INSERT INTO journal_acces (tenant_id, utilisateur_id, ticket_id, action, ip)
				VALUES (?, ?, ?, ?, ?)
				""", tenantId, utilisateurId, ticketId, action, ip);
	}
}
