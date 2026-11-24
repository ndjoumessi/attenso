package ai.attenso.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès aux notifications en SQL direct, et non par l'ORM. La raison est le
 * plafond : {@code ON CONFLICT (ticket_id, type) DO NOTHING} laisse l'index
 * unique refuser le doublon <em>sans lever d'exception</em>, là où un
 * {@code INSERT} par l'ORM empoisonnerait la transaction de la transition en
 * cours. L'index reste le mécanisme, le code ne peut pas le contourner.
 */
@Repository
public class NotificationDao {

	/** Au-delà, on arrête d'essayer : un numéro invalide ne devient pas valide. */
	public static final int TENTATIVES_MAX = 3;

	private final JdbcTemplate jdbc;

	public NotificationDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return vrai si la ligne a été créée, faux si le plafond l'a refusée. */
	public boolean creer(UUID id, UUID tenantId, UUID ticketId, TypeNotification type,
			Canal canal, String destinataire, String corps, BigDecimal coutEstime) {
		return jdbc.update("""
				INSERT INTO notification
				  (id, tenant_id, ticket_id, type, canal, destinataire, corps, statut,
				   cout_centimes)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'EN_ATTENTE', ?)
				ON CONFLICT (ticket_id, type) DO NOTHING
				""", id, tenantId, ticketId, type.name(), canal.name(), destinataire, corps,
				coutEstime) == 1;
	}

	/**
	 * La même chose pour un rendez-vous (EF-905) ou un rappel de vaccin
	 * (EF-906), qui ne sont attachés à aucun ticket.
	 *
	 * <p>Deux méthodes plutôt qu'un paramètre nullable de plus : chacune porte
	 * son propre {@code ON CONFLICT}, et les deux index partiels qui tiennent
	 * le plafond d'EF-303 sont ainsi nommés à l'endroit où ils agissent.
	 */
	/** Le pendant pour un rappel de vaccin (EF-906). */
	public boolean creerPourVaccination(UUID id, UUID tenantId, UUID vaccinationId,
			TypeNotification type, Canal canal, String destinataire, String corps,
			BigDecimal coutEstime) {
		return jdbc.update("""
				INSERT INTO notification
				  (id, tenant_id, vaccination_id, type, canal, destinataire, corps, statut,
				   cout_centimes)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'EN_ATTENTE', ?)
				ON CONFLICT (vaccination_id, type) WHERE vaccination_id IS NOT NULL
				DO NOTHING
				""", id, tenantId, vaccinationId, type.name(), canal.name(), destinataire,
				corps, coutEstime) == 1;
	}

	public boolean creerPourRendezVous(UUID id, UUID tenantId, UUID rendezVousId,
			TypeNotification type, Canal canal, String destinataire, String corps,
			BigDecimal coutEstime) {
		return jdbc.update("""
				INSERT INTO notification
				  (id, tenant_id, rendez_vous_id, type, canal, destinataire, corps, statut,
				   cout_centimes)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'EN_ATTENTE', ?)
				ON CONFLICT (rendez_vous_id, type) WHERE rendez_vous_id IS NOT NULL
				DO NOTHING
				""", id, tenantId, rendezVousId, type.name(), canal.name(), destinataire,
				corps, coutEstime) == 1;
	}

	public List<ANotifier> enAttente(UUID tenantId, int limite) {
		return jdbc.query("""
				SELECT id, ticket_id, canal, destinataire, corps, tentatives FROM notification
				WHERE tenant_id = ? AND statut = 'EN_ATTENTE' AND tentatives < ?
				ORDER BY cree_le LIMIT ?
				""",
				(ligne, i) -> new ANotifier(ligne.getObject("id", UUID.class),
						ligne.getObject("ticket_id", UUID.class),
						Canal.valueOf(ligne.getString("canal")),
						ligne.getString("destinataire"), ligne.getString("corps"),
						ligne.getInt("tentatives")),
				tenantId, TENTATIVES_MAX, limite);
	}

	public void marquerEnvoye(UUID id, String fournisseurId, BigDecimal cout,
			BigDecimal coutFournisseur, Instant quand) {
		jdbc.update("""
				UPDATE notification
				SET statut = 'ENVOYE', fournisseur_id = ?, cout_centimes = ?,
				    cout_fournisseur = ?, envoye_le = ?, tentatives = tentatives + 1,
				    erreur = NULL
				WHERE id = ?
				""", fournisseurId, cout, coutFournisseur, java.sql.Timestamp.from(quand), id);
	}

	/**
	 * Abandon définitif, sans compter de tentative : sert au cas « clinique
	 * sans expéditeur enregistré », où réessayer ne servirait à rien tant que
	 * la validation opérateur n'est pas faite.
	 */
	/**
	 * Abandonne les notifications en attente d'un seul canal.
	 *
	 * <p>Le canal est devenu un paramètre le jour où le push est arrivé : une
	 * clinique dont l'expéditeur SMS n'est pas encore enregistré doit voir ses
	 * SMS abandonnés, pas ses notifications push, qui ne dépendent d'aucune
	 * validation opérateur.
	 */
	public void abandonner(UUID tenantId, Canal canal, String motif) {
		jdbc.update("""
				UPDATE notification SET statut = 'ECHEC', erreur = ?
				WHERE tenant_id = ? AND statut = 'EN_ATTENTE' AND canal = ?
				""", motif, tenantId, canal.name());
	}

	public void marquerEchec(UUID id, String erreur) {
		jdbc.update("""
				UPDATE notification
				SET tentatives = tentatives + 1,
				    erreur = ?,
				    statut = CASE WHEN tentatives + 1 >= ? THEN 'ECHEC' ELSE 'EN_ATTENTE' END
				WHERE id = ?
				""", erreur, TENTATIVES_MAX, id);
	}

	/** Coût réel des SMS d'une journée, en centimes. La marge se lit ici. */
	public BigDecimal coutDuJourCentimes(UUID tenantId, LocalDate jour) {
		BigDecimal total = jdbc.queryForObject("""
				SELECT coalesce(sum(n.cout_centimes), 0) FROM notification n
				JOIN ticket t ON t.id = n.ticket_id
				WHERE n.tenant_id = ? AND t.jour = ? AND n.statut IN ('ENVOYE', 'LIVRE')
				""", BigDecimal.class, tenantId, jour);
		return total == null ? BigDecimal.ZERO : total;
	}

	public long compterParType(UUID tenantId, LocalDate jour, TypeNotification type) {
		Long total = jdbc.queryForObject("""
				SELECT count(*) FROM notification n
				JOIN ticket t ON t.id = n.ticket_id
				WHERE n.tenant_id = ? AND t.jour = ? AND n.type = ?
				""", Long.class, tenantId, jour, type.name());
		return total == null ? 0 : total;
	}
}
