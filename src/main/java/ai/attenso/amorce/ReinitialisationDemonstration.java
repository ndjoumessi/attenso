package ai.attenso.amorce;

import ai.attenso.tenant.ContexteTenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rejoue la démonstration : efface ce que la clinique de démonstration a
 * accumulé, puis ressème la file du jour, la veille, l'agenda et les carnets.
 *
 * <p><strong>Elle détruit des données. Trois verrous, cumulatifs.</strong>
 * <ol>
 * <li>Le bean n'existe que si {@code attenso.amorce.reinitialiser} est vrai
 *     (fausse par défaut). C'est la condition de classe ci-dessous, et rien
 *     d'autre : relire la propriété dans {@link #run} serait du code mort,
 *     toujours vrai là où il est lu (c'était le défaut de l'ancien
 *     {@code if (proprietes.active())} d'{@link AmorceClinique}). Ce verrou-là
 *     est donc tenu par un test qui interroge le conteneur, pas par un
 *     {@code if}.</li>
 * <li>{@code attenso.amorce.demonstration} doit être vrai aussi. Ce n'est PAS
 *     une condition de classe, donc ce {@code if} est vivant : le bean existe
 *     bien avec {@code demonstration} à faux.</li>
 * <li>La clinique visée doit se nommer {@value #SLUG_DE_LA_DEMONSTRATION}, lu
 *     <em>en base</em> sur la ligne qu'on s'apprête à vider, et pas seulement
 *     dans la configuration : {@code slug=cerisiers} avec les deux autres
 *     verrous posés ne vide pas une vraie clinique.</li>
 * </ol>
 *
 * <p>Chaque refus est journalisé en {@code WARN} avec sa raison, et ne touche
 * à rien. {@code active} n'est pas une condition de classe non plus : sans
 * {@link AmorceClinique} (qui sème), la commande refuse au lieu de ne rien
 * dire.
 *
 * <p><strong>Chirurgicale.</strong> Aucun {@code TRUNCATE}, aucun
 * {@code DELETE} sur {@code tenant} : les lignes de cette seule clinique, dans
 * l'ordre des clés étrangères (aucune n'a de {@code ON DELETE CASCADE}). Le
 * tout, effacement et semis, dans une seule transaction : un semis qui échoue
 * rend la démonstration d'avant, pas une clinique vide.
 *
 * <p>Elle passe avant {@link AmorceClinique} : sur une base neuve elle ne
 * trouve rien à vider et le semis d'installation s'en charge ; sur une clinique
 * existante, {@code run} d'{@link AmorceClinique} sort ensuite au garde-fou du
 * slug.
 */
@Component
@ConditionalOnProperty(name = "attenso.amorce.reinitialiser", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReinitialisationDemonstration implements ApplicationRunner {

	/** Le seul slug que cette commande accepte de vider. */
	static final String SLUG_DE_LA_DEMONSTRATION = "demo";

	/** La bande de {@code tenant_delai_grace_positif} (V1__socle.sql). */
	static final int GRACE_MIN_SECONDES = 0;

	static final int GRACE_MAX_SECONDES = 3600;

	private static final Logger JOURNAL = LoggerFactory
			.getLogger(ReinitialisationDemonstration.class);

	/**
	 * Ce qu'on efface, dans l'ordre : les enfants avant leurs parents. Chaque
	 * requête porte {@code tenant_id} en plus du RLS : la première barrière, au
	 * cas où la seconde serait contournée (rôle propriétaire).
	 *
	 * <p>{@code notification} d'abord (elle référence ticket, rendez-vous et
	 * vaccination), {@code creneau} après les rendez-vous, {@code animal} après
	 * ses vaccinations. Les deux journaux en ajout seul ne sont PAS vidés :
	 * leur lien vers un ticket est détaché, la ligne reste.
	 */
	private static final List<String> ETAPES = List.of(
			"DELETE FROM notification WHERE tenant_id = ?",
			"DELETE FROM push_abonnement WHERE tenant_id = ?",
			"UPDATE journal_acces SET ticket_id = NULL WHERE tenant_id = ? AND ticket_id IS NOT NULL",
			"UPDATE commande SET ticket_id = NULL WHERE tenant_id = ? AND ticket_id IS NOT NULL",
			"DELETE FROM ticket WHERE tenant_id = ?",
			"DELETE FROM mesure_jour WHERE tenant_id = ?",
			"DELETE FROM rendez_vous WHERE tenant_id = ?",
			"DELETE FROM creneau WHERE tenant_id = ?",
			"DELETE FROM plage_horaire WHERE tenant_id = ?",
			"DELETE FROM vaccination WHERE tenant_id = ?",
			"DELETE FROM animal WHERE tenant_id = ?");

	private final ProprietesAmorce proprietes;

	private final JdbcTemplate jdbc;

	private final ObjectProvider<AmorceClinique> amorce;

	private final TransactionTemplate transaction;

	public ReinitialisationDemonstration(ProprietesAmorce proprietes, JdbcTemplate jdbc,
			ObjectProvider<AmorceClinique> amorce, TransactionTemplate transaction) {
		this.proprietes = proprietes;
		this.jdbc = jdbc;
		this.amorce = amorce;
		this.transaction = transaction;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (!proprietes.demonstration()) {
			refuser("attenso.amorce.demonstration n'est pas vrai : ce n'est pas une "
					+ "clinique de démonstration");
			return;
		}
		AmorceClinique semeur = amorce.getIfAvailable();
		if (semeur == null) {
			refuser("attenso.amorce.active n'est pas vrai : rien ne pourrait ressemer");
			return;
		}
		String configure = proprietes.slug();
		if (configure == null || configure.isBlank()) {
			refuser("attenso.amorce.slug est vide : aucune clinique désignée");
			return;
		}

		// On lit la base : la configuration dit quelle clinique on VEUT vider, la
		// ligne dit laquelle on viderait.
		List<Map<String, Object>> lignes = jdbc.queryForList(
				"SELECT id, slug FROM tenant WHERE slug = ?", configure);
		if (lignes.isEmpty()) {
			JOURNAL.info("réinitialisation : la clinique « {} » n'existe pas, rien à vider — "
					+ "l'amorce va l'installer", configure);
			return;
		}
		Map<String, Object> clinique = lignes.get(0);
		String enBase = (String) clinique.get("slug");
		if (!SLUG_DE_LA_DEMONSTRATION.equals(enBase)) {
			refuser("la clinique « " + enBase + " » n'est pas « " + SLUG_DE_LA_DEMONSTRATION
					+ " » : une vraie clinique ne se réinitialise pas");
			return;
		}

		UUID tenantId = (UUID) clinique.get("id");
		// Le tenant se lie AVANT la transaction : la connexion en reçoit le tenant à son
		// acquisition (SourceDonneesCloisonnee), c'est-à-dire à l'ouverture de la
		// transaction, et non au premier ordre SQL.
		ContexteTenant.executer(tenantId, () -> transaction.executeWithoutResult(statut -> {
			// AVANT le semis : le ticket appelé en tire son échéance.
			aligner(tenantId);
			effacer(tenantId);
			semeur.semerLaDemonstration(tenantId);
		}));
		JOURNAL.info("réinitialisation : démonstration « {} » rejouée", enBase);
	}

	/**
	 * Aligne la grâce de la clinique sur {@code attenso.amorce.delai-grace-secondes},
	 * qui ne servait qu'à la création : la réinitialisation conserve le locataire,
	 * donc sa grâce d'origine. Sans valeur configurée, la clinique garde la sienne.
	 * Une valeur hors de la contrainte du schéma est bornée plutôt que de faire
	 * échouer la transaction, et le dit.
	 */
	private void aligner(UUID tenantId) {
		Integer demandee = proprietes.delaiGraceSecondes();
		if (demandee == null) {
			return;
		}
		int appliquee = Math.max(GRACE_MIN_SECONDES, Math.min(GRACE_MAX_SECONDES, demandee));
		jdbc.update("UPDATE tenant SET delai_grace_secondes = ? WHERE id = ?", appliquee, tenantId);
		if (appliquee != demandee) {
			JOURNAL.warn("réinitialisation : grâce demandée {} s, appliquée {} s — la contrainte "
					+ "du schéma (tenant_delai_grace_positif) n'accepte que {} à {} s",
					demandee, appliquee, GRACE_MIN_SECONDES, GRACE_MAX_SECONDES);
		}
		else {
			JOURNAL.info("réinitialisation : grâce de la clinique portée à {} s", appliquee);
		}
	}

	private void effacer(UUID tenantId) {
		for (String requete : ETAPES) {
			jdbc.update(requete, tenantId);
		}
	}

	private static void refuser(String raison) {
		JOURNAL.warn("réinitialisation REFUSÉE, rien n'a été touché : {}", raison);
	}
}
