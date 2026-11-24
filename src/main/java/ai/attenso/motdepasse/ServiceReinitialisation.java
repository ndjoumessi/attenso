package ai.attenso.motdepasse;

import ai.attenso.courriel.ServiceCourriel;
import ai.attenso.courriel.TravailDiffere;
import ai.attenso.securite.GenerateurReference;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Récupération de mot de passe.
 *
 * <p>Sans elle, une clinique qui perd son mot de passe est enfermée dehors
 * définitivement : il n'existe aucun autre chemin vers son tableau.
 *
 * <p><strong>Rien de ce que renvoie {@link #demander} ne dit si le compte
 * existe.</strong> Un message différent selon le cas transformerait ce
 * formulaire en annuaire : il suffirait d'essayer des adresses pour savoir
 * lesquelles sont clientes, information qui se revend. La page répond donc la
 * même chose à tout le monde, et c'est au courriel — ou à son absence — de
 * faire la différence.
 */
@Service
public class ServiceReinitialisation {

	private static final Logger JOURNAL =
			LoggerFactory.getLogger(ServiceReinitialisation.class);

	/**
	 * Une heure. Assez pour aller chercher ses courriels sur un autre appareil,
	 * assez court pour qu'un lien oublié dans une boîte partagée ne soit plus
	 * une clé.
	 */
	public static final Duration VALIDITE = Duration.ofHours(1);

	private final TenantRepository tenants;

	private final ReinitialisationDao dao;

	private final ServiceCourriel courriels;

	private final GenerateurReference jetons;

	private final PasswordEncoder motsDePasse;

	private final Clock horloge;

	private final String baseUrl;

	private final JdbcTemplate jdbc;

	private final TravailDiffere differe;

	private final TransactionTemplate transaction;

	public ServiceReinitialisation(TenantRepository tenants, ReinitialisationDao dao,
			ServiceCourriel courriels, GenerateurReference jetons,
			PasswordEncoder motsDePasse, Clock horloge, JdbcTemplate jdbc,
			TravailDiffere differe, TransactionTemplate transaction,
			@Value("${attenso.lien.base}") String baseUrl) {
		this.tenants = tenants;
		this.dao = dao;
		this.courriels = courriels;
		this.jetons = jetons;
		this.motsDePasse = motsDePasse;
		this.horloge = horloge;
		this.jdbc = jdbc;
		this.differe = differe;
		this.transaction = transaction;
		this.baseUrl = baseUrl;
	}

	/**
	 * Ouvre une demande et envoie le lien, si et seulement si le compte existe.
	 *
	 * <p>Ne rend rien, et ne fait RIEN sur le fil de la requête. C'est la
	 * seconde moitié de la promesse « la réponse ne dit pas si le compte
	 * existe » : répondre pareil ne suffit pas si l'on répond plus LENTEMENT
	 * quand le compte existe. Un envoi SMTP prend des centaines de
	 * millisecondes, là où un compte inconnu repartait en deux ou trois — un
	 * écart qui se chronomètre depuis n'importe où, et l'énumération revenait
	 * par la porte de derrière. Tout est donc remis à un fil d'exécution, y
	 * compris la recherche de la clinique : la requête repart en temps
	 * constant, quoi qu'il arrive ensuite.
	 *
	 * <p>Pas de délai artificiel ajouté : il ralentirait tout le monde sans
	 * rien garantir de plus, puisque le travail ne se mesure plus.
	 */
	public void demander(String slug, String email) {
		differe.plusTard(() -> {
			try {
				transaction.executeWithoutResult(etat -> traiter(slug, email));
			}
			catch (RuntimeException panne) {
				// Hors du fil de la requête, une exception ne remonte nulle
				// part : sans ce filet, une panne de base ferait disparaître
				// les demandes en silence.
				JOURNAL.error("demande de réinitialisation en échec : {}",
						panne.getMessage());
			}
		});
	}

	private void traiter(String slug, String email) {
		Optional<Tenant> clinique = tenants.findBySlugAndActifTrue(slug);
		if (clinique.isEmpty()) {
			return;
		}
		UUID tenantId = clinique.get().id();
		lier(tenantId);
		ContexteTenant.executer(tenantId, () -> dao.utilisateurActif(tenantId, email)
				.ifPresent(utilisateurId -> envoyer(clinique.get(), utilisateurId)));
	}

	private void envoyer(Tenant clinique, UUID utilisateurId) {
		String jeton = jetons.suivante() + jetons.suivante();
		Instant maintenant = horloge.instant();
		dao.ouvrir(UUID.randomUUID(), clinique.id(), utilisateurId, empreinte(jeton),
				maintenant.plus(VALIDITE), maintenant);

		String lien = baseUrl + "/reinitialisation/" + jeton;
		boolean parti = courriels.envoyer(dao.emailDe(utilisateurId),
				"Attenso — réinitialiser votre mot de passe", """
						Bonjour,

						Une réinitialisation du mot de passe a été demandée pour la \
						clinique « %s ».

						Pour choisir un nouveau mot de passe, ouvrez ce lien dans \
						l'heure qui vient :
						%s

						Si vous n'êtes pas à l'origine de cette demande, ignorez ce \
						message : votre mot de passe reste inchangé.
						""".formatted(clinique.nom(), lien));
		if (!parti) {
			// On garde la demande ouverte : l'exploitant peut encore dépanner,
			// et l'échec n'est pas la faute du client.
			JOURNAL.error("lien de réinitialisation non envoyé pour la clinique {}",
					clinique.slug());
		}
	}

	/**
	 * Pose le nouveau mot de passe, si le jeton est encore valable.
	 *
	 * @return vrai si le mot de passe a changé.
	 */
	@Transactional
	public boolean reinitialiser(String jeton, String nouveauMotDePasse) {
		String empreinte = empreinte(jeton);
		UUID tenantId = dao.tenantDe(empreinte).orElse(null);
		if (tenantId == null) {
			return false;
		}
		lier(tenantId);
		return ContexteTenant.avec(tenantId, () ->
				dao.consommer(empreinte, horloge.instant()).map(utilisateurId -> {
					dao.poserLeMotDePasse(utilisateurId,
							motsDePasse.encode(nouveauMotDePasse), horloge.instant());
					JOURNAL.info("mot de passe réinitialisé pour un compte de la "
							+ "clinique {}", tenantId);
					return true;
				}).orElse(false));
	}

	/**
	 * Lie la clinique sur LA CONNEXION COURANTE.
	 *
	 * <p>Lier le ThreadLocal ne suffit pas : {@code SourceDonneesCloisonnee}
	 * pousse le réglage au moment où la connexion est obtenue, or
	 * {@code @Transactional} l'a déjà ouverte — avant que l'on sache de quelle
	 * clinique on parle. Sans ça, les politiques RLS masquent la table
	 * {@code utilisateur} et la demande d'oubli ne trouve jamais personne. Même
	 * parade qu'à l'inscription et dans le webhook de paiement ; c'est la
	 * troisième fois que ce piège se présente dans ce dépôt.
	 */
	private void lier(UUID tenantId) {
		jdbc.queryForObject("SELECT set_config('attenso.tenant_id', ?, true)", String.class,
				tenantId.toString());
	}

	/**
	 * L'empreinte du jeton, pour ne jamais stocker le jeton lui-même.
	 *
	 * <p>SHA-256 nu et non bcrypt, contrairement aux mots de passe : ce jeton
	 * fait 24 caractères tirés au hasard, il n'a ni dictionnaire ni réutilisation
	 * à redouter, et il vit une heure. Un bcrypt par vérification coûterait du
	 * temps de calcul pour une résistance dont il n'a pas besoin.
	 */
	static String empreinte(String jeton) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(jeton.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 absent de la plateforme", impossible);
		}
	}
}
