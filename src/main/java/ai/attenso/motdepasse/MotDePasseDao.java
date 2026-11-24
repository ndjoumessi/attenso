package ai.attenso.motdepasse;

import java.time.Instant;
import java.util.List;
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
	 * Pose le mot de passe, la date du changement et la nouvelle version.
	 *
	 * <p>Les trois dans le MÊME ordre SQL : séparés, une panne entre deux
	 * laisserait un mot de passe changé sans que les sessions ouvertes soient
	 * fermées. La date sert d'information ; c'est le compteur de version qui
	 * ferme réellement les sessions.
	 *
	 * @return la version du jeton après incrément. Elle sert à réémettre un
	 *         jeton pour celui qui vient de changer son mot de passe : il doit
	 *         rester à l'écran, ce sont les AUTRES sessions qu'on ferme.
	 */
	public int poserLeMotDePasse(UUID utilisateurId, String empreinte, Instant quand) {
		return jdbc.queryForObject("""
				UPDATE utilisateur
				SET mdp_hash = ?, mdp_change_le = ?, jeton_version = jeton_version + 1
				WHERE id = ?
				RETURNING jeton_version
				""", Integer.class, empreinte, java.sql.Timestamp.from(quand),
				utilisateurId);
	}
}
