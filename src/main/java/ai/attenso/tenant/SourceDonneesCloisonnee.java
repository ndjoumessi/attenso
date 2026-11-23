package ai.attenso.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Pousse le tenant courant dans la session PostgreSQL à chaque acquisition de
 * connexion, pour que les politiques RLS de {@code V2__rls.sql} s'appliquent.
 *
 * <p>Vide quand aucun tenant n'est lié : les politiques comparent alors à NULL
 * et masquent tout. C'est voulu — fermé par défaut.
 */
public class SourceDonneesCloisonnee extends DelegatingDataSource {

	private static final String POSER_TENANT = "SELECT set_config('attenso.tenant_id', ?, false)";

	public SourceDonneesCloisonnee(DataSource cible) {
		super(cible);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return poser(super.getConnection());
	}

	@Override
	public Connection getConnection(String utilisateur, String motDePasse) throws SQLException {
		return poser(super.getConnection(utilisateur, motDePasse));
	}

	private Connection poser(Connection connexion) throws SQLException {
		String tenant = ContexteTenant.courant().map(Object::toString).orElse("");
		try (PreparedStatement requete = connexion.prepareStatement(POSER_TENANT)) {
			requete.setString(1, tenant);
			requete.execute();
		}
		catch (SQLException echec) {
			connexion.close();
			throw echec;
		}
		return connexion;
	}
}
