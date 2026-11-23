package ai.attenso.integration;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.utilisateur.Role;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Socle des tests d'intégration : un vrai PostgreSQL, et surtout <strong>deux
 * rôles</strong>.
 *
 * <p>Ce détail n'est pas du décor. Les politiques RLS ne s'appliquent pas au
 * propriétaire des tables : tester avec un seul rôle donnerait des tests verts
 * sur un cloisonnement inexistant en production. Flyway migre donc en
 * propriétaire, et l'application tourne en {@code attenso_app} — exactement
 * comme dans {@code compose.yaml}.
 *
 * <p>Les ordonnanceurs sont repoussés à l'infini : les tâches sont appelées à
 * la main, pour que les tests ne dépendent pas d'un délai.
 */
@SpringBootTest
public abstract class SocleIntegration {

	protected static final String ROLE_APPLICATIF = "attenso_app";

	private static final String MDP_APPLICATIF = "mdp_de_test";

	private static final String JAMAIS = "0 0 5 31 2 *";

	/**
	 * Un seul conteneur pour toutes les classes de test, démarré ici et jamais
	 * arrêté (Ryuk le nettoie à la fin du JVM).
	 *
	 * <p>Ce n'est pas une optimisation. Avec {@code @Container}, JUnit arrête le
	 * conteneur à la fin de chaque classe ; la classe suivante en démarre un
	 * neuf et vide, alors que le contexte Spring est mis en cache et ne rejoue
	 * donc pas les migrations. On obtient un schéma absent sur une base
	 * joignable, c'est-à-dire la panne la plus déroutante possible.
	 */
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void proprietes(DynamicPropertyRegistry registre) {
		registre.add("spring.flyway.url", POSTGRES::getJdbcUrl);
		registre.add("spring.flyway.user", POSTGRES::getUsername);
		registre.add("spring.flyway.password", POSTGRES::getPassword);
		registre.add("spring.flyway.placeholders.roleApplicatif", () -> ROLE_APPLICATIF);
		registre.add("spring.flyway.placeholders.mdpRoleApplicatif", () -> MDP_APPLICATIF);

		registre.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registre.add("spring.datasource.username", () -> ROLE_APPLICATIF);
		registre.add("spring.datasource.password", () -> MDP_APPLICATIF);

		registre.add("attenso.jeton.secret",
				() -> "secret-de-test-assez-long-pour-hmac-sha256-aucun-usage-reel");
		registre.add("attenso.lien.base", () -> "https://attenso.test");

		registre.add("attenso.file.periode-tick", () -> 3_600_000);
		registre.add("attenso.sms.periode-balayage", () -> 3_600_000);
		registre.add("attenso.file.cron-purge", () -> JAMAIS);
	}

	@Autowired
	protected JdbcTemplate applicatif;

	@Autowired
	protected PasswordEncoder motsDePasse;

	/** Connexion propriétaire : sert au nettoyage, hors RLS. */
	protected JdbcTemplate proprietaire;

	@BeforeEach
	void preparerLeSocle() {
		DriverManagerDataSource source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		this.proprietaire = new JdbcTemplate(source);
		viderLesTables();
	}

	@AfterEach
	void delierLeTenant() {
		ContexteTenant.delier();
	}

	private void viderLesTables() {
		proprietaire.execute("""
				TRUNCATE commande, journal_acces, notification, ticket, utilisateur, tenant
				CASCADE
				""");
	}

	protected UUID creerClinique(String slug, String nom, int seuilPrevenir,
			int delaiGraceSecondes) {
		UUID id = UUID.randomUUID();
		proprietaire.update("""
				INSERT INTO tenant (id, slug, nom, fuseau, delai_grace_secondes,
				                    seuil_prevenir, actif)
				VALUES (?, ?, ?, 'Europe/Paris', ?, ?, true)
				""", id, slug, nom, delaiGraceSecondes, seuilPrevenir);
		return id;
	}

	protected UUID creerUtilisateur(UUID tenantId, String email, String motDePasse, Role role) {
		UUID id = UUID.randomUUID();
		proprietaire.update("""
				INSERT INTO utilisateur (id, tenant_id, email, mdp_hash, role, actif)
				VALUES (?, ?, ?, ?, ?, true)
				""", id, tenantId, email, motsDePasse.encode(motDePasse), role.name());
		return id;
	}

	/** Compte les notifications d'un type, vues depuis le propriétaire (hors RLS). */
	protected long compterNotifications(UUID tenantId, String type) {
		Long total = proprietaire.queryForObject("""
				SELECT count(*) FROM notification WHERE tenant_id = ? AND type = ?
				""", Long.class, tenantId, type);
		return total == null ? 0 : total;
	}

	protected String dernierCorps(UUID tenantId, String type) {
		return proprietaire.queryForObject("""
				SELECT corps FROM notification WHERE tenant_id = ? AND type = ?
				ORDER BY cree_le DESC LIMIT 1
				""", String.class, tenantId, type);
	}

	protected DataSource sourceApplicative() {
		return applicatif.getDataSource();
	}
}
