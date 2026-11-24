package ai.attenso.motdepasse;

import ai.attenso.utilisateur.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Le seul endroit où un mot de passe s'écrit.
 *
 * <p>Deux chemins y mènent — le lien reçu par courriel, et le changement
 * volontaire depuis le tableau — et ils doivent faire exactement la même
 * chose. Séparés, l'un des deux finirait par oublier de fermer les sessions
 * ouvertes, et ce serait découvert le jour où quelqu'un change son mot de
 * passe précisément parce qu'il le croit volé.
 */
@Repository
public class MotDePasseDao {

	private final JdbcTemplate jdbc;

	public MotDePasseDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * L'empreinte actuelle, pour vérifier le mot de passe que l'on remplace.
	 *
	 * <p>Rend {@code null} si le compte n'existe pas ou n'est pas visible : la
	 * vérification échouera, ce qui est le bon résultat.
	 */
	public String hashDe(UUID utilisateurId) {
		List<String> trouve = jdbc.query(
				"SELECT mdp_hash FROM utilisateur WHERE id = ? AND actif = true",
				(ligne, i) -> ligne.getString(1), utilisateurId);
		return trouve.isEmpty() ? null : trouve.get(0);
	}

	/**
	 * Pose le mot de passe, la date du changement et la nouvelle version, et
	 * rend l'état de la ligne au même instant.
	 *
	 * <p>Tout dans le MÊME ordre SQL, et ce n'est pas de l'élégance. Écriture
	 * et lecture séparées laisseraient une fenêtre entre les deux : un gérant
	 * qui rétrograde ou désactive ce compte pile à ce moment verrait le
	 * changement de mot de passe rendre un jeton neuf, valide douze heures, et
	 * portant le privilège qu'il vient justement de retirer. Renouvelable à
	 * volonté, puisqu'il suffit de changer son mot de passe à nouveau. Le rôle
	 * rendu ici est celui de la base, pas celui du jeton de l'appelant.
	 *
	 * <p>{@code AND actif = true} pour la même raison, et il porte en plus la
	 * garantie que le mot de passe d'un compte désactivé ne change pas.
	 *
	 * @return vide si la ligne n'existe pas ou n'est plus active ; rien n'a
	 *         alors été écrit.
	 */
	public Optional<EtatApresChangement> poserLeMotDePasse(UUID utilisateurId,
			String empreinte, Instant quand) {
		List<EtatApresChangement> pose = jdbc.query("""
				UPDATE utilisateur
				SET mdp_hash = ?, mdp_change_le = ?, jeton_version = jeton_version + 1
				WHERE id = ? AND actif = true
				RETURNING jeton_version, role
				""",
				(ligne, i) -> new EtatApresChangement(ligne.getInt(1),
						Role.valueOf(ligne.getString(2))),
				empreinte, java.sql.Timestamp.from(quand), utilisateurId);
		return pose.isEmpty() ? Optional.empty() : Optional.of(pose.get(0));
	}
}
