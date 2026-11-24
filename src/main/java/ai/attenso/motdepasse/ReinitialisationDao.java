package ai.attenso.motdepasse;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les demandes de réinitialisation, et leur usage unique. */
@Repository
public class ReinitialisationDao {

	private final JdbcTemplate jdbc;

	public ReinitialisationDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * La clinique derrière un jeton encore valable.
	 *
	 * <p>Par la fonction {@code SECURITY DEFINER} de {@code V19} : le porteur
	 * du lien n'a rien d'autre que son jeton, ni slug ni compte, et il faut
	 * connaître la clinique AVANT de pouvoir lire quoi que ce soit sous RLS.
	 */
	public Optional<UUID> tenantDe(String empreinte) {
		return jdbc.query("SELECT attenso_tenant_de_reinitialisation(?)",
				ligne -> ligne.next() ? Optional.ofNullable(ligne.getObject(1, UUID.class))
						: Optional.<UUID>empty(),
				empreinte);
	}

	/**
	 * Annule les demandes en cours d'un utilisateur, puis en ouvre une.
	 *
	 * <p>Une seule demande valable à la fois : sinon, trois demandes faites
	 * dans la panique laisseraient trois liens actifs, dont deux que personne
	 * ne surveille.
	 */
	public void ouvrir(UUID id, UUID tenantId, UUID utilisateurId, String empreinte,
			Instant expireLe, Instant maintenant) {
		jdbc.update("""
				UPDATE reinitialisation SET utilise_le = ?
				WHERE utilisateur_id = ? AND utilise_le IS NULL
				""", java.sql.Timestamp.from(maintenant), utilisateurId);
		jdbc.update("""
				INSERT INTO reinitialisation
				  (id, tenant_id, utilisateur_id, jeton_hash, expire_le)
				VALUES (?, ?, ?, ?, ?)
				""", id, tenantId, utilisateurId, empreinte,
				java.sql.Timestamp.from(expireLe));
	}

	/**
	 * Consomme le jeton et rend l'utilisateur concerné.
	 *
	 * <p>Le {@code RETURNING} après un {@code UPDATE} conditionnel, et non un
	 * SELECT suivi d'un UPDATE : deux requêtes laisseraient deux appels
	 * simultanés consommer le même jeton, et poser deux mots de passe
	 * différents dont un seul serait connu de son auteur.
	 */
	public Optional<UUID> consommer(String empreinte, Instant maintenant) {
		return jdbc.query("""
				UPDATE reinitialisation SET utilise_le = ?
				WHERE jeton_hash = ? AND utilise_le IS NULL AND expire_le > ?
				RETURNING utilisateur_id
				""",
				ligne -> ligne.next() ? Optional.ofNullable(ligne.getObject(1, UUID.class))
						: Optional.<UUID>empty(),
				java.sql.Timestamp.from(maintenant), empreinte,
				java.sql.Timestamp.from(maintenant));
	}

	/** L'utilisateur actif de cette clinique portant cette adresse. */
	public Optional<UUID> utilisateurActif(UUID tenantId, String email) {
		return jdbc.query("""
				SELECT id FROM utilisateur
				WHERE tenant_id = ? AND lower(email) = lower(?) AND actif = true
				LIMIT 1
				""",
				ligne -> ligne.next() ? Optional.ofNullable(ligne.getObject(1, UUID.class))
						: Optional.<UUID>empty(),
				tenantId, email);
	}

	public String emailDe(UUID utilisateurId) {
		return jdbc.queryForObject("SELECT email FROM utilisateur WHERE id = ?",
				String.class, utilisateurId);
	}

	/**
	 * Pose le mot de passe ET la date du changement.
	 *
	 * <p>Les trois dans le MÊME ordre SQL : séparés, une panne entre deux
	 * laisserait un mot de passe changé sans que les sessions ouvertes soient
	 * fermées — exactement le cas où l'on change son mot de passe parce qu'on
	 * le croit volé. La date sert d'information ; c'est le compteur de version
	 * qui ferme réellement les sessions.
	 */
	public void poserLeMotDePasse(UUID utilisateurId, String empreinte, Instant quand) {
		jdbc.update("""
				UPDATE utilisateur
				SET mdp_hash = ?, mdp_change_le = ?, jeton_version = jeton_version + 1
				WHERE id = ?
				""", empreinte, java.sql.Timestamp.from(quand), utilisateurId);
	}
}
