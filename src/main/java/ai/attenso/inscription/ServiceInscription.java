package ai.attenso.inscription;

import ai.attenso.facturation.FacturationDao;
import ai.attenso.paiement.PasserellePaiement;
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
import org.springframework.transaction.support.TransactionTemplate;

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

	private final PasserellePaiement paiement;

	private final TransactionTemplate transaction;

	public ServiceInscription(TenantRepository tenants, JdbcTemplate jdbc,
			PasswordEncoder motsDePasse, MotifDao motifs, FacturationDao facturation,
			GenerateurReference jetons, Clock horloge, PasserellePaiement paiement,
			TransactionTemplate transaction) {
		this.tenants = tenants;
		this.jdbc = jdbc;
		this.motsDePasse = motsDePasse;
		this.motifs = motifs;
		this.facturation = facturation;
		this.jetons = jetons;
		this.horloge = horloge;
		this.paiement = paiement;
		this.transaction = transaction;
	}

	/**
	 * Crée l'établissement et ouvre sa page de paiement.
	 *
	 * <p><strong>L'ordre compte.</strong> Stripe est appelé AVANT d'écrire quoi
	 * que ce soit, avec un identifiant de clinique tiré ici : un appel réseau
	 * dans une transaction ouverte tiendrait une connexion de base pendant que
	 * l'on attend un tiers, et un échec après l'écriture laisserait une
	 * clinique orpheline qu'un second essai dupliquerait. Si Stripe ne répond
	 * pas, rien n'a été écrit et l'inscription est à refaire, simplement.
	 *
	 * <p>La clinique naît INACTIVE. C'est le webhook de paiement qui l'ouvre.
	 * {@code tenant.actif} était déjà le drapeau honoré partout dans le
	 * produit — depuis {@code findBySlugAndActifTrue} jusqu'aux tâches
	 * planifiées — donc l'abonnement impayé n'a pas eu besoin d'une seconde
	 * notion de « clinique fermée » qu'il aurait fallu faire respecter
	 * partout.
	 */
	public ReponseInscription inscrire(DemandeInscription demande) {
		Palier palier = lirePalier(demande.palier());
		String email = demande.email().trim().toLowerCase();
		String slug = slugLibre(Slug.depuis(demande.nom()));
		UUID tenantId = UUID.randomUUID();
		String jetonEcran = demande.avecEcran() ? jetons.suivante() : null;

		PasserellePaiement.Souscription souscription =
				paiement.ouvrir(tenantId, palier, email, demande.nom().trim());

		transaction.executeWithoutResult(etat ->
				ecrire(tenantId, slug, demande, palier, email, jetonEcran));

		JOURNAL.info("inscription autonome : « {} » → /t/{}, palier {}, en attente de "
				+ "paiement", demande.nom(), slug, palier);
		return new ReponseInscription(slug, "/t/" + slug,
				jetonEcran == null ? null : "/ecran/" + jetonEcran, palier.name(),
				NomExpediteur.proposerDepuis(demande.nom()), souscription.url());
	}

	private void ecrire(UUID tenantId, String slug, DemandeInscription demande,
			Palier palier, String email, String jetonEcran) {
		jdbc.update("""
				INSERT INTO tenant (id, slug, nom, fuseau, delai_grace_secondes,
				                    seuil_prevenir, actif, expediteur_sms, jeton_ecran,
				                    cgv_acceptees_le)
				VALUES (?, ?, ?, 'Europe/Paris', 180, 2, false, NULL, ?, ?)
				""", tenantId, slug, demande.nom().trim(), jetonEcran,
				java.sql.Timestamp.from(horloge.instant()));

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
			facturation.souscrire(tenantId, palier, LocalDate.now(horloge), "EN_ATTENTE");
		});
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
		// Sans tenir compte des cliniques INACTIVES, une inscription en attente
		// de paiement laisserait son adresse paraître libre, et la suivante du
		// même nom buterait sur l'index unique au milieu de son inscription.
		if (!tenants.existsBySlug(base)) {
			return base;
		}
		for (int suffixe = 2; suffixe <= SUFFIXES_ESSAYES; suffixe++) {
			String candidat = base + "-" + suffixe;
			if (!tenants.existsBySlug(candidat)) {
				return candidat;
			}
		}
		throw new IllegalStateException("aucun slug libre pour « " + base + " »");
	}
}
