package ai.attenso.equipe;

import ai.attenso.utilisateur.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les comptes du personnel d'une clinique. */
@Repository
public class EquipeDao {

	/**
	 * Marqueur posé à la création, à la place d'une empreinte de mot de passe.
	 *
	 * <p>Ce n'est pas une empreinte valable : aucun mot de passe ne peut y
	 * correspondre, donc le compte est inutilisable tant que son titulaire n'a
	 * pas suivi son invitation. Mettre un mot de passe provisoire aurait créé
	 * un secret qui circule et qui dure.
	 */
	static final String SANS_MOT_DE_PASSE = "!invitation-en-attente";

	private final JdbcTemplate jdbc;

	public EquipeDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<MembreEquipe> lister(UUID tenantId) {
		return jdbc.query("""
				SELECT id, email, role, actif, mdp_hash, cree_le FROM utilisateur
				WHERE tenant_id = ? ORDER BY actif DESC, email
				""",
				(ligne, i) -> new MembreEquipe(ligne.getObject("id", UUID.class),
						ligne.getString("email"), Role.valueOf(ligne.getString("role")),
						ligne.getBoolean("actif"),
						!SANS_MOT_DE_PASSE.equals(ligne.getString("mdp_hash")),
						ligne.getTimestamp("cree_le").toInstant()),
				tenantId);
	}

	/** @return faux si l'adresse est déjà prise dans cette clinique. */
	public boolean creer(UUID id, UUID tenantId, String email, Role role,
			Instant maintenant) {
		return jdbc.update("""
				INSERT INTO utilisateur (id, tenant_id, email, mdp_hash, role, actif, cree_le)
				VALUES (?, ?, ?, ?, ?, true, ?)
				ON CONFLICT (tenant_id, email) DO NOTHING
				""", id, tenantId, email, SANS_MOT_DE_PASSE, role.name(),
				java.sql.Timestamp.from(maintenant)) == 1;
	}

	public Optional<MembreEquipe> parId(UUID tenantId, UUID id) {
		return lister(tenantId).stream().filter(membre -> membre.id().equals(id))
				.findFirst();
	}

	/**
	 * Change le rôle, et INVALIDE les jetons du compte.
	 *
	 * <p>Le rôle voyage dans le jeton : sans l'incrément, une rétrogradation
	 * ne prendrait effet qu'à l'expiration, c'est-à-dire jusqu'à douze heures
	 * plus tard. Or on rétrograde quelqu'un précisément quand on ne veut plus
	 * qu'il puisse faire quelque chose.
	 */
	public void changerRole(UUID tenantId, UUID id, Role role) {
		jdbc.update("""
				UPDATE utilisateur SET role = ?, jeton_version = jeton_version + 1
				WHERE tenant_id = ? AND id = ?
				""", role.name(), tenantId, id);
	}

	/**
	 * Active ou désactive, et invalide les jetons dans les deux sens.
	 *
	 * <p>À la désactivation, c'est l'essentiel : un départ de salarié ne doit
	 * pas laisser une session ouverte jusqu'au soir.
	 */
	public void changerActif(UUID tenantId, UUID id, boolean actif) {
		jdbc.update("""
				UPDATE utilisateur SET actif = ?, jeton_version = jeton_version + 1
				WHERE tenant_id = ? AND id = ?
				""", actif, tenantId, id);
		if (!actif) {
			// Les invitations et demandes d'oubli EN COURS sont closes avec le
			// compte. Sans ça, une personne invitée puis désactivée avant
			// d'avoir suivi son lien garderait un lien valable pendant sept
			// jours : elle ne pourrait pas se connecter — la connexion exige
			// un compte actif — mais elle POSERAIT son mot de passe, et le
			// jour d'une réactivation le compte s'ouvrirait sur un secret
			// qu'elle est seule à connaître.
			jdbc.update("""
					UPDATE reinitialisation SET utilise_le = now()
					WHERE tenant_id = ? AND utilisateur_id = ? AND utilise_le IS NULL
					""", tenantId, id);
		}
	}

	/** Combien de gérants actifs, hors celui qu'on s'apprête à changer. */
	public int compterGerantsActifsSauf(UUID tenantId, UUID exclu) {
		Integer compte = jdbc.queryForObject("""
				SELECT count(*) FROM utilisateur
				WHERE tenant_id = ? AND actif = true AND role = 'GERANT' AND id <> ?
				""", Integer.class, tenantId, exclu);
		return compte == null ? 0 : compte;
	}
}
