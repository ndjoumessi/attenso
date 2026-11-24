package ai.attenso.push;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les abonnements push, un par ticket au plus. */
@Repository
public class PushDao {

	private final JdbcTemplate jdbc;

	public PushDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Enregistre ou remplace l'abonnement d'un ticket.
	 *
	 * <p>Remplacement et non ajout : un client qui réautorise les
	 * notifications, ou qui ouvre son lien sur un second appareil, ne doit pas
	 * recevoir deux fois le même « c'est presque à vous ».
	 */
	public void poser(UUID tenantId, UUID ticketId, AbonnementPush abonnement) {
		jdbc.update("""
				INSERT INTO push_abonnement (tenant_id, ticket_id, endpoint, cle_p256dh, cle_auth)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (ticket_id) DO UPDATE
				SET endpoint = EXCLUDED.endpoint, cle_p256dh = EXCLUDED.cle_p256dh,
				    cle_auth = EXCLUDED.cle_auth, cree_le = now()
				""", tenantId, ticketId, abonnement.endpoint(), abonnement.clePublique(),
				abonnement.cleAuth());
	}

	public Optional<AbonnementPush> pourTicket(UUID tenantId, UUID ticketId) {
		return jdbc.query("""
				SELECT endpoint, cle_p256dh, cle_auth FROM push_abonnement
				WHERE tenant_id = ? AND ticket_id = ?
				""",
				(ligne, i) -> new AbonnementPush(ligne.getString("endpoint"),
						ligne.getString("cle_p256dh"), ligne.getString("cle_auth")),
				tenantId, ticketId).stream().findFirst();
	}

	/**
	 * Un abonnement que l'opérateur de push a déclaré mort (404 ou 410).
	 *
	 * <p>Le retirer est la seule réponse correcte : le réessayer produirait le
	 * même refus indéfiniment, et la spécification demande explicitement de
	 * nettoyer. Le ticket bascule alors sur le SMS au prochain message.
	 */
	/**
	 * Purge les abonnements des journées passées (EC-03).
	 *
	 * <p>Un abonnement push est un identifiant stable de navigateur : il meurt
	 * avec le ticket, exactement comme le numéro de mobile. Sans cette purge,
	 * ce produit deviendrait un traceur.
	 */
	public int purgerAvant(UUID tenantId, java.time.LocalDate jour) {
		return jdbc.update("""
				DELETE FROM push_abonnement WHERE tenant_id = ? AND ticket_id IN (
				    SELECT id FROM ticket WHERE tenant_id = ? AND jour < ?)
				""", tenantId, tenantId, jour);
	}

	public void retirer(UUID tenantId, String endpoint) {
		jdbc.update("DELETE FROM push_abonnement WHERE tenant_id = ? AND endpoint = ?",
				tenantId, endpoint);
	}
}
