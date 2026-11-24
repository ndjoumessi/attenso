package ai.attenso.securite;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuse les jetons émis avant le dernier changement de mot de passe.
 *
 * <p>Sans ce contrôle, changer son mot de passe PARCE QU'ON LE CROIT VOLÉ ne
 * fermait rien : le voleur gardait sa session jusqu'à douze heures, la durée
 * d'un jeton du personnel, c'est-à-dire tout le temps qu'il lui fallait.
 *
 * <p><strong>Oui, c'est une lecture en base par requête authentifiée</strong>,
 * et c'est le prix assumé d'un jeton révocable. Elle porte sur une clé
 * primaire dans {@code utilisateur}, une table qui compte quelques lignes par
 * clinique et tient donc entière en cache. Elle ne s'ajoute qu'aux requêtes
 * du PERSONNEL : le chemin client — page de suivi, mur, prise de ticket — ne
 * porte pas de jeton de personnel et n'est pas touché, or c'est lui qui porte
 * la contrainte de latence d'ENF-01.
 */
@Component
public class SessionsRevoquees {

	private final JdbcTemplate jdbc;

	public SessionsRevoquees(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param emisLe l'instant d'émission du jeton.
	 * @return vrai si le jeton a été émis avant le dernier changement de mot
	 *         de passe, et doit donc être refusé.
	 */
	public boolean revoque(UUID utilisateurId, Instant emisLe) {
		java.sql.Timestamp change = jdbc.query("""
				SELECT mdp_change_le FROM utilisateur WHERE id = ?
				""", ligne -> ligne.next() ? ligne.getTimestamp(1) : null, utilisateurId);
		// Pas de ligne : le compte a disparu depuis l'émission du jeton. Le
		// refuser est la bonne réponse — c'est aussi ce qui coupe l'accès d'un
		// compte supprimé sans attendre l'expiration.
		if (change == null) {
			return estInconnu(utilisateurId);
		}
		// LES DEUX CÔTÉS À LA SECONDE. L'instant d'émission d'un JWT est
		// tronqué à la seconde par la spécification ; la date en base, elle,
		// porte les millisecondes. Comparer l'un à l'autre tel quel rejetait
		// une connexion faite dans la MÊME seconde que la réinitialisation —
		// c'est-à-dire le cas le plus courant, puisqu'on se reconnecte juste
		// après avoir changé son mot de passe. Un test l'a attrapé ; le
		// raisonnement, lui, disait l'inverse.
		//
		// Ce qui subsiste : un jeton émis dans la même seconde que le
		// changement survit. Une seconde contre l'impossibilité de se
		// reconnecter, le choix est vite fait.
		return emisLe.isBefore(change.toInstant().truncatedTo(ChronoUnit.SECONDS));
	}

	private boolean estInconnu(UUID utilisateurId) {
		Integer compte = jdbc.queryForObject(
				"SELECT count(*) FROM utilisateur WHERE id = ?", Integer.class,
				utilisateurId);
		return compte == null || compte == 0;
	}
}
