package ai.attenso.amorce;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.NomExpediteur;
import ai.attenso.priorite.MotifDao;
import ai.attenso.securite.GenerateurReference;
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

	private final GenerateurReference jetons;

	private final MotifDao motifs;

	public AmorceClinique(ProprietesAmorce proprietes, TenantRepository tenants,
			JdbcTemplate jdbc, PasswordEncoder motsDePasse, GenerateurReference jetons,
			MotifDao motifs) {
		this.proprietes = proprietes;
		this.tenants = tenants;
		this.jdbc = jdbc;
		this.motsDePasse = motsDePasse;
		this.jetons = jetons;
		this.motifs = motifs;
	}

	/**
	 * Motifs propres à la clinique de démonstration, en plus des deux défauts.
	 *
	 * <p>Les défauts portent sur l'humain accompagnant et sont posés au même
	 * rang : une clinique qui les voit seuls ne perçoit ni que la priorité est
	 * graduée, ni qu'elle peut écrire ses propres motifs. Ceux-ci parlent de
	 * l'animal, avec des rangs étagés. Ils restent ici et non dans
	 * {@code MotifDao.DEFAUTS} : « bétaillère » n'a pas de sens pour une
	 * clinique urbaine, et changer les défauts changerait le produit pour tous.
	 *
	 * <p>Pas de garde propre : on n'arrive ici que par la création de la
	 * clinique, que {@code run} ne refait jamais quand le slug existe déjà.
	 * Les rangs restent dans la bande 1 à 9 de {@code motif_rang_dans_la_bande} :
	 * aucun ne peut rejoindre l'urgence (10).
	 */
	private void poserLesMotifsDeDemonstration(UUID tenantId) {
		// « Contagieux » tout en haut : l'isoler protège toute la salle d'attente,
		// pas seulement l'animal concerné.
		// « Chat en caisse » et « bétaillère » sont les exemples que cite
		// V8__motifs_priorite.sql : une attente prolongée en caisse, un animal de
		// ferme qui ne peut pas patienter dans sa remorque.
		// « Contrôle post-opératoire » en dessous des défauts : c'est du confort,
		// pas une dégradation, il passe devant la file normale sans doubler personne
		// de plus pressé.
		short ordre = (short) (MotifDao.DEFAUTS.size() + 1);
		motifs.ajouter(tenantId, "Animal potentiellement contagieux", (short) 8, ordre++);
		motifs.ajouter(tenantId, "Chat en caisse depuis plus de 30 min", (short) 7, ordre++);
		motifs.ajouter(tenantId, "Animal de ferme en bétaillère", (short) 6, ordre++);
		motifs.ajouter(tenantId, "Contrôle post-opératoire court", (short) 3, ordre);
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

		String expediteur = proprietes.expediteurSms();
		if (expediteur != null && !NomExpediteur.valide(expediteur)) {
			throw new IllegalArgumentException(("expéditeur SMS « %s » non conforme : 3 à 11 "
					+ "caractères alphanumériques sans accent. Proposition : « %s »")
					.formatted(expediteur, NomExpediteur.proposerDepuis(proprietes.nom())));
		}

		UUID tenantId = UUID.randomUUID();
		// Pas d'écran demandé, pas de jeton : la clinique n'en a tout
		// simplement pas, et ses routes d'écran répondront 404 (EF-603).
		String jetonEcran = proprietes.avecEcran() ? jetons.suivante() : null;
		jdbc.update("""
				INSERT INTO tenant (id, slug, nom, fuseau, delai_grace_secondes,
				                    seuil_prevenir, actif, expediteur_sms, jeton_ecran)
				VALUES (?, ?, ?, 'Europe/Paris', ?, ?, true, ?, ?)
				""", tenantId, proprietes.slug(), proprietes.nom(),
				proprietes.delaiGraceSecondes() == null ? 180 : proprietes.delaiGraceSecondes(),
				proprietes.seuilPrevenir() == null ? 2 : proprietes.seuilPrevenir(), expediteur,
				jetonEcran);

		// La table « utilisateur » EST soumise au RLS : sans tenant lié, la
		// politique refuserait l'insertion (WITH CHECK compare à NULL). Il faut
		// donc lier le tenant qu'on vient de créer. C'est la preuve que la
		// seconde barrière fonctionne, y compris contre notre propre code.
		ContexteTenant.executer(tenantId, () -> jdbc.update("""
				INSERT INTO utilisateur (id, tenant_id, email, mdp_hash, role, actif)
				VALUES (?, ?, ?, ?, ?, true)
				""", UUID.randomUUID(), tenantId, proprietes.email().trim().toLowerCase(),
				motsDePasse.encode(proprietes.motDePasse()), Role.GERANT.name()));

		// Les deux motifs de priorité du cahier des charges (EF-407). La
		// clinique les renomme, les désactive ou en ajoute ensuite.
		ContexteTenant.executer(tenantId, () -> {
			motifs.poserLesDefauts(tenantId);
			poserLesMotifsDeDemonstration(tenantId);
		});

		JOURNAL.info("clinique « {} » installée — comptoir : /t/{}",
				proprietes.nom(), proprietes.slug());
		if (jetonEcran != null) {
			// Journalisé une fois, à l'installation : c'est l'URL qu'on colle
			// dans le navigateur du téléviseur. Elle n'est plus jamais
			// affichée ailleurs, et la retrouver demande un accès à la base.
			JOURNAL.info("écran de salle d'attente appairé — /ecran/{}", jetonEcran);
		}
		else {
			JOURNAL.info("clinique « {} » sans écran de salle d'attente : aucune route "
					+ "d'écran ne répondra pour elle", proprietes.slug());
		}
	}
}
