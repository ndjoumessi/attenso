package ai.attenso.amorce;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.TenantRepository;
import ai.attenso.utilisateur.Role;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée la clinique du pilote et son compte d'accueil, une fois.
 *
 * <p>Idempotent : relancer ne fait rien si le slug existe déjà. Les écritures
 * passent par {@code JdbcTemplate} parce qu'au démarrage aucun tenant n'est
 * lié, donc les politiques RLS masqueraient la table {@code utilisateur} — et
 * c'est exactement ce qu'on veut qu'elles fassent le reste du temps.
 */
@Component
@ConditionalOnProperty(name = "attenso.amorce.active", havingValue = "true")
public class AmorceClinique implements ApplicationRunner {

	private static final Logger JOURNAL = LoggerFactory.getLogger(AmorceClinique.class);

	private final ProprietesAmorce proprietes;

	private final TenantRepository tenants;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder motsDePasse;

	public AmorceClinique(ProprietesAmorce proprietes, TenantRepository tenants,
			JdbcTemplate jdbc, PasswordEncoder motsDePasse) {
		this.proprietes = proprietes;
		this.tenants = tenants;
		this.jdbc = jdbc;
		this.motsDePasse = motsDePasse;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (proprietes.slug() == null || proprietes.motDePasse() == null
				|| proprietes.motDePasse().isBlank()) {
			JOURNAL.warn("amorçage demandé sans slug ni mot de passe : rien à faire");
			return;
		}
		if (tenants.findBySlugAndActifTrue(proprietes.slug()).isPresent()) {
			JOURNAL.info("clinique « {} » déjà installée", proprietes.slug());
			return;
		}

		UUID tenantId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO tenant (id, slug, nom, fuseau, delai_grace_secondes,
				                    seuil_prevenir, actif)
				VALUES (?, ?, ?, 'Europe/Paris', ?, ?, true)
				""", tenantId, proprietes.slug(), proprietes.nom(),
				proprietes.delaiGraceSecondes() == null ? 180 : proprietes.delaiGraceSecondes(),
				proprietes.seuilPrevenir() == null ? 2 : proprietes.seuilPrevenir());

		// La table « utilisateur » EST soumise au RLS : sans tenant lié, la
		// politique refuserait l'insertion (WITH CHECK compare à NULL). Il faut
		// donc lier le tenant qu'on vient de créer. C'est la preuve que la
		// seconde barrière fonctionne, y compris contre notre propre code.
		ContexteTenant.executer(tenantId, () -> jdbc.update("""
				INSERT INTO utilisateur (id, tenant_id, email, mdp_hash, role, actif)
				VALUES (?, ?, ?, ?, ?, true)
				""", UUID.randomUUID(), tenantId, proprietes.email().trim().toLowerCase(),
				motsDePasse.encode(proprietes.motDePasse()), Role.ACCUEIL.name()));

		JOURNAL.info("clinique « {} » installée — comptoir : /t/{} — écran : /ecran/{}",
				proprietes.nom(), proprietes.slug(), proprietes.slug());
	}
}
