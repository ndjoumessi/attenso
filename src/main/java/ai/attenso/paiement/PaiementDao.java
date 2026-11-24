package ai.attenso.paiement;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** L'état de paiement d'un abonnement, et l'idempotence des webhooks. */
@Repository
public class PaiementDao {

	private final JdbcTemplate jdbc;

	public PaiementDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * La clinique derrière un abonnement Stripe.
	 *
	 * <p>Par la fonction {@code SECURITY DEFINER} de {@code V17} : le webhook
	 * arrive sans contexte de cloisonnement, et c'est le seul chemin de
	 * lecture hors RLS. Il ne rend qu'un identifiant de clinique.
	 */
	public Optional<UUID> tenantDe(String abonnementStripe) {
		return jdbc.query("SELECT attenso_tenant_de_abonnement_stripe(?)",
				ligne -> ligne.next() ? Optional.ofNullable(ligne.getObject(1, UUID.class))
						: Optional.<UUID>empty(),
				abonnementStripe);
	}

	/**
	 * Le paiement est confirmé : l'abonnement en attente devient actif et
	 * retient ses identifiants Stripe.
	 *
	 * @return vrai si une ligne a changé. Faux veut dire que l'abonnement a
	 *         déjà été lié — un rejeu, pas une anomalie.
	 */
	public boolean activer(UUID tenantId, String clientStripe, String abonnementStripe) {
		int lignes = jdbc.update("""
				UPDATE abonnement
				SET statut = 'ACTIF', stripe_client_id = ?, stripe_abonnement_id = ?
				WHERE tenant_id = ? AND fin IS NULL AND statut <> 'RESILIE'
				  AND (stripe_abonnement_id IS NULL OR stripe_abonnement_id = ?)
				""", clientStripe, abonnementStripe, tenantId, abonnementStripe);
		if (lignes > 0) {
			// C'est ICI que la clinique ouvre vraiment. « actif » était déjà le
			// drapeau honoré partout — connexion du personnel, QR du comptoir,
			// tâches planifiées — donc le paiement n'a pas eu besoin d'une
			// seconde notion de clinique fermée à faire respecter ailleurs.
			jdbc.update("UPDATE tenant SET actif = true WHERE id = ?", tenantId);
		}
		return lignes > 0;
	}

	/** Passe l'abonnement en cours dans un état, sans toucher à ses dates. */
	public boolean marquer(UUID tenantId, String statut) {
		return jdbc.update("""
				UPDATE abonnement SET statut = ?
				WHERE tenant_id = ? AND fin IS NULL AND statut <> 'RESILIE'
				""", statut, tenantId) > 0;
	}

	/**
	 * Résiliation : l'abonnement est clos à une date, et c'est cette date qui
	 * fait foi. La clinique garde son service jusque-là — elle a payé sa
	 * période.
	 */
	public boolean resilier(UUID tenantId, LocalDate fin) {
		return jdbc.update("""
				UPDATE abonnement SET statut = 'RESILIE', fin = ?
				WHERE tenant_id = ? AND fin IS NULL
				""", fin, tenantId) > 0;
	}

	/**
	 * Ferme la clinique si son abonnement résilié est arrivé à son terme.
	 *
	 * <p>Par clinique et non d'un seul UPDATE balayant la table : les
	 * politiques RLS s'appliquent au rôle applicatif, donc un balayage global
	 * ne verrait qu'une clinique — ou aucune. La tâche lie chaque clinique à
	 * son tour, exactement comme la purge RGPD.
	 *
	 * <p>Pas au moment de la résiliation : la clinique a payé sa période et la
	 * garde jusqu'au bout. La comparaison est stricte, donc la fermeture a
	 * lieu le LENDEMAIN du dernier jour payé.
	 *
	 * @return vrai si la clinique vient d'être fermée.
	 */
	public boolean fermerSiEchu(UUID tenantId, LocalDate jour) {
		return jdbc.update("""
				UPDATE tenant SET actif = false
				WHERE id = ? AND actif = true AND EXISTS (
				  SELECT 1 FROM abonnement
				  WHERE tenant_id = ? AND statut = 'RESILIE'
				    AND fin IS NOT NULL AND fin < ?)
				""", tenantId, tenantId, jour) > 0;
	}

	public Optional<String> statutCourant(UUID tenantId) {
		return jdbc.query("""
				SELECT statut FROM abonnement WHERE tenant_id = ? AND fin IS NULL
				ORDER BY debut DESC LIMIT 1
				""", ligne -> ligne.next() ? Optional.ofNullable(ligne.getString(1))
						: Optional.<String>empty(), tenantId);
	}

	/**
	 * Enregistre un événement Stripe, et dit s'il est NOUVEAU.
	 *
	 * <p>Stripe rejoue ses événements à la moindre réponse lente. C'est la
	 * clé primaire qui tient l'unicité, pas un {@code if} : deux webhooks
	 * simultanés pour le même événement ne peuvent pas passer tous les deux.
	 *
	 * @return vrai s'il faut le traiter, faux s'il l'a déjà été.
	 */
	public boolean enregistrerSiNouveau(String evenementId, String type) {
		return jdbc.update("""
				INSERT INTO paiement_evenement (id, type) VALUES (?, ?)
				ON CONFLICT (id) DO NOTHING
				""", evenementId, type) == 1;
	}
}
