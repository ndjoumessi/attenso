package ai.attenso.securite;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuse les jetons dont la version n'est plus celle du compte.
 *
 * <p>Sans ce contrôle, changer son mot de passe PARCE QU'ON LE CROIT VOLÉ ne
 * fermait rien : le voleur gardait sa session jusqu'à douze heures, la durée
 * d'un jeton du personnel, c'est-à-dire tout le temps qu'il lui fallait.
 *
 * <p><strong>Un compteur, et non une comparaison de dates.</strong> La
 * première version comparait l'instant d'émission du jeton à la date du
 * changement, et deux granularités s'y rencontraient — un JWT porte son
 * émission à la seconde, la colonne portait les millisecondes. Cette rencontre
 * a produit deux erreurs successives : d'abord le rejet des reconnexions faites
 * dans la même seconde que le changement, puis, une fois les deux côtés
 * tronqués, la survie d'un jeton émis pendant cette seconde-là. Un compteur n'a
 * pas de granularité : il s'incrémente, on compare pour égalité, et il n'y a
 * plus de fenêtre à raisonner.
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
	 * @return vrai si le jeton doit être refusé : version périmée, ou compte
	 *         disparu depuis l'émission — ce second cas coupe aussi l'accès
	 *         d'un compte supprimé sans attendre l'expiration.
	 */
	public boolean revoque(UUID utilisateurId, int versionDuJeton) {
		Integer version = jdbc.query("""
				SELECT jeton_version FROM utilisateur WHERE id = ?
				""", ligne -> ligne.next() ? ligne.getInt(1) : null, utilisateurId);
		return version == null || version != versionDuJeton;
	}
}
