package ai.attenso.integration;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.utilisateur.Role;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import(ai.attenso.paiement.PasserelleFausse.Config.class)
public abstract class SocleIntegration {

	protected static final String ROLE_APPLICATIF = "attenso_app";

	private static final String MDP_APPLICATIF = "mdp_de_test";

	private static final String JAMAIS = "0 0 5 31 2 *";

	/** Secret de signature des webhooks, pour les tests seulement. */
	protected static final String SECRET_WEBHOOK = "whsec_secret_de_test";

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

		// Stripe est configuré POUR DE BON côté secret de webhook : la
		// vérification de signature est le seul rempart de cette route, et la
		// tester avec un secret factice mais un vrai HMAC est tout l'intérêt.
		// La clé d'API, elle, ne sert jamais — la passerelle est remplacée.
		registre.add("attenso.paiement.cle", () -> "sk_test_jamais_appelee");
		registre.add("attenso.paiement.secret-webhook", () -> SECRET_WEBHOOK);
		registre.add("attenso.paiement.prix.comptoir", () -> "price_comptoir");
		registre.add("attenso.paiement.prix.clinique", () -> "price_clinique");
		registre.add("attenso.paiement.prix.centre", () -> "price_centre");
		registre.add("attenso.paiement.cron-fin", () -> JAMAIS);

		// Une identité d'éditeur complète, sinon chaque test journaliserait une
		// ERROR « vous vendez sans mentions légales » — et une ERROR à chaque
		// exécution apprend à ignorer les ERROR. La règle elle-même est testée
		// à part, dans VeilleMentionsLegalesTest.
		registre.add("attenso.editeur.denomination", () -> "Attenso (tests)");
		registre.add("attenso.editeur.siege", () -> "1 rue de Test, 13000 Marseille");
		registre.add("attenso.editeur.siret", () -> "00000000000000");
		registre.add("attenso.editeur.directeur-publication", () -> "Prénom Nom");
		registre.add("attenso.editeur.email", () -> "contact@attenso.test");
		registre.add("attenso.editeur.hebergeur", () -> "Hébergeur de test");
		registre.add("attenso.editeur.hebergeur-adresse", () -> "2 rue de Test, 75000 Paris");
	}

	@Autowired
	protected JdbcTemplate applicatif;

	@Autowired
	protected PasswordEncoder motsDePasse;

	/** Connexion propriétaire : sert au nettoyage, hors RLS. */
	protected JdbcTemplate proprietaire;

	@Autowired
	protected ai.attenso.web.LimiteurDebit limiteur;

	@BeforeEach
	void preparerLeSocle() {
		// Le limiteur est en mémoire et vit aussi longtemps que le contexte
		// Spring, donc d'un cas de test au suivant : sans ce nettoyage, le
		// quatrième appel d'une classe se fait refuser pour une raison qui
		// n'a rien à voir avec ce qu'elle teste.
		limiteur.vider();
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

	/** Clinique dont l'expéditeur SMS est enregistré : le cas normal. */
	protected UUID creerClinique(String slug, String nom, int seuilPrevenir,
			int delaiGraceSecondes) {
		return creerClinique(slug, nom, seuilPrevenir, delaiGraceSecondes, "Attenso");
	}

	/**
	 * @param expediteurSms {@code null} pour une clinique dont le nom n'est pas
	 *        encore validé par les opérateurs — état réel pendant toute
	 *        l'installation d'un nouveau client.
	 */
	protected UUID creerClinique(String slug, String nom, int seuilPrevenir,
			int delaiGraceSecondes, String expediteurSms) {
		UUID id = UUID.randomUUID();
		proprietaire.update("""
				INSERT INTO tenant (id, slug, nom, fuseau, delai_grace_secondes,
				                    seuil_prevenir, actif, expediteur_sms)
				VALUES (?, ?, ?, 'Europe/Paris', ?, ?, true, ?)
				""", id, slug, nom, delaiGraceSecondes, seuilPrevenir, expediteurSms);
		return id;
	}

	/**
	 * Appaire un écran de salle d'attente et rend son jeton (EF-601).
	 *
	 * <p>Séparé de la création : une clinique sans écran est le cas par défaut
	 * (EF-603), et les tests doivent pouvoir l'exprimer sans rien passer.
	 */
	protected String appairerEcran(UUID tenantId, String jeton) {
		proprietaire.update("UPDATE tenant SET jeton_ecran = ? WHERE id = ?", jeton, tenantId);
		return jeton;
	}

	protected String statutNotification(UUID tenantId, String type) {
		return proprietaire.queryForObject("""
				SELECT statut FROM notification WHERE tenant_id = ? AND type = ?
				ORDER BY cree_le DESC LIMIT 1
				""", String.class, tenantId, type);
	}

	protected String erreurNotification(UUID tenantId) {
		return proprietaire.queryForObject("""
				SELECT erreur FROM notification WHERE tenant_id = ?
				ORDER BY cree_le DESC LIMIT 1
				""", String.class, tenantId);
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
