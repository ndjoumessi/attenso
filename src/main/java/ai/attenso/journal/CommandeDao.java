package ai.attenso.journal;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Journal des commandes reçues, pour le rejeu des actions faites hors ligne.
 *
 * <p>La clé primaire <em>est</em> l'idempotence : l'UUID est généré par la
 * tablette de l'accueil avant l'envoi, et une seconde arrivée de la même
 * commande n'insère rien. Sans ça, une reconnexion après vingt minutes de
 * panne rejouerait toute la journée.
 */
@Repository
public class CommandeDao {

	private final JdbcTemplate jdbc;

	public CommandeDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return vrai si la commande est nouvelle, faux si elle a déjà été traitée. */
	public boolean premiereFois(UUID commandeId, UUID tenantId, UUID ticketId, String action) {
		return jdbc.update("""
				INSERT INTO commande (id, tenant_id, ticket_id, action)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (id) DO NOTHING
				""", commandeId, tenantId, ticketId, action) == 1;
	}
}
