package ai.attenso.inscription;

import ai.attenso.facturation.FacturationDao;
import ai.attenso.facturation.Palier;
import ai.attenso.priorite.MotifDao;
import ai.attenso.securite.GenerateurReference;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.NomExpediteur;
import ai.attenso.tenant.TenantRepository;
import ai.attenso.utilisateur.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription autonome d'un établissement (EF-901).
 *
 * <p><strong>Ce qui n'est pas autonome, et qui est annoncé.</strong> Le §15 du
 * cahier des charges l'avait vu : l'expéditeur des SMS porte le nom de la
 * clinique, ce qui exige une autorisation écrite et une validation opérateur
 * de plusieurs jours. Aucune inscription en ligne ne peut produire ça. La
 * sortie est déjà dans le schéma : {@code expediteur_sms} reste NULL, la
 * clinique tourne <em>sans SMS</em> — file, écran et page de suivi compris —
 * et la réponse d'inscription le dit en toutes lettres plutôt que de laisser
 * découvrir le silence.
 */
@Service
public class ServiceInscription {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceInscription.class);

	/** Au-delà, c'est que le nom est déjà pris par trop de monde : on abandonne. */
	private static final int SUFFIXES_ESSAYES = 50;

	private final TenantRepository tenants;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder motsDePasse;

	private final MotifDao motifs;

	private final FacturationDao facturation;

	private final GenerateurReference jetons;

	private final Clock horloge;

	public ServiceInscription(TenantRepository tenants, JdbcTemplate jdbc,
			PasswordEncoder motsDePasse, MotifDao motifs, FacturationDao facturation,
			GenerateurReference jetons, Clock horloge) {
		this.tenants = tenants;
		this.jdbc = jdbc;
		this.motsDePasse = motsDePasse;
		this.motifs = motifs;
		this.facturation = facturation;
		this.jetons = jetons;
		this.horloge = horloge;
	}

	@Transactional
	public ReponseInscription inscrire(DemandeInscription demande) {
		Palier palier = lirePalier(demande.palier());
		String email = demande.email().trim().toLowerCase();
		String slug = slugLibre(Slug.depuis(demande.nom()));
		UUID tenantId = UUID.randomUUID();
		String jetonEcran = demande.avecEcran() ? jetons.suivante() : null;

		jdbc.update("""
				INSERT INTO tenant (id, slug, nom, fuseau, delai_grace_secondes,
				                    seuil_prevenir, actif, expediteur_sms, jeton_ecran)
				VALUES (?, ?, ?, 'Europe/Paris', 180, 2, true, NULL, ?)
				""", tenantId, slug, demande.nom().trim(), jetonEcran);

		// La table « utilisateur » est soumise au RLS : sans tenant lié, la
		// politique refuse l'insertion. C'est la preuve que la seconde
		// barrière fonctionne, y compris contre notre propre code.
		//
		// Et il ne suffit PAS de lier le ThreadLocal ici : SourceDonneesCloisonnee
		// pousse le réglage au moment où la connexion est obtenue, or nous
		// sommes déjà dans une transaction, donc sur une connexion ouverte
		// avant que le tenant n'existe. On pose donc le réglage sur CETTE
		// connexion, en portée transactionnelle (« true »), pour qu'il
		// disparaisse au commit.
		jdbc.queryForObject("SELECT set_config('attenso.tenant_id', ?, true)", String.class,
				tenantId.toString());
		ContexteTenant.executer(tenantId, () -> {
			jdbc.update("""
					INSERT INTO utilisateur (id, tenant_id, email, mdp_hash, role, actif)
					VALUES (?, ?, ?, ?, ?, true)
					""", UUID.randomUUID(), tenantId, email,
					motsDePasse.encode(demande.motDePasse()), Role.ACCUEIL.name());
			motifs.poserLesDefauts(tenantId);
			facturation.souscrire(tenantId, palier, LocalDate.now(horloge));
		});

		JOURNAL.info("inscription autonome : « {} » → /t/{}, palier {}", demande.nom(), slug,
				palier);
		return new ReponseInscription(slug, "/t/" + slug,
				jetonEcran == null ? null : "/ecran/" + jetonEcran, palier.name(),
				NomExpediteur.proposerDepuis(demande.nom()));
	}

	private static Palier lirePalier(String demande) {
		try {
			return Palier.valueOf(demande.trim().toUpperCase());
		}
		catch (IllegalArgumentException inconnu) {
			throw new PalierInconnu(demande);
		}
	}

	/**
	 * Un slug libre, dérivé du nom.
	 *
	 * <p>Deux « Clinique du Parc » existent dans deux villes : la seconde
	 * reçoit {@code clinique-du-parc-2}. Rendre une erreur obligerait à
	 * inventer un nom différent du vrai, qui finirait imprimé sur l'affiche du
	 * comptoir.
	 */
	private String slugLibre(String base) {
		if (tenants.findBySlugAndActifTrue(base).isEmpty()) {
			return base;
		}
		for (int suffixe = 2; suffixe <= SUFFIXES_ESSAYES; suffixe++) {
			String candidat = base + "-" + suffixe;
			if (tenants.findBySlugAndActifTrue(candidat).isEmpty()) {
				return candidat;
			}
		}
		throw new IllegalStateException("aucun slug libre pour « " + base + " »");
	}
}
