package ai.attenso.supervision;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Supervision et alerte en cas d'indisponibilité (EF-903).
 *
 * <p>Trois contrôles, choisis parce qu'ils correspondent à trois pannes
 * réellement vues plutôt qu'à des métriques génériques :
 *
 * <ul>
 * <li><strong>la base répond</strong> — sans elle il n'y a plus de file ;</li>
 * <li><strong>les SMS partent</strong> — l'application peut répondre
 * parfaitement pendant que la file d'envoi est bloquée. De l'extérieur tout va
 * bien ; pour le client qui attend son « revenez », le produit est en
 * panne ;</li>
 * <li><strong>la tâche de grâce tourne</strong> — si l'ordonnanceur s'arrête,
 * plus aucun absent n'est détecté et la file se fige sans que rien n'échoue.
 * C'est la panne la plus sournoise des trois.</li>
 * </ul>
 *
 * <p>L'alerte ne part qu'au <em>changement</em> d'état. Répéter le message
 * toutes les minutes pendant une panne de nuit fait couper les notifications,
 * et la panne suivante passe alors inaperçue.
 */
@Service
public class Surveillance {

	private static final Logger JOURNAL = LoggerFactory.getLogger(Surveillance.class);

	private final JdbcTemplate jdbc;

	private final ProprietesSupervision proprietes;

	private final Clock horloge;

	private final RestClient http;

	/** Dernier état publié : sert à n'alerter qu'aux transitions. */
	private volatile EtatService dernier;

	public Surveillance(JdbcTemplate jdbc, ProprietesSupervision proprietes, Clock horloge) {
		this.jdbc = jdbc;
		this.proprietes = proprietes;
		this.horloge = horloge;
		// RestClient.Builder n'est pas auto-configuré dans cette application :
		// on construit le client à la main.
		this.http = RestClient.builder().build();
		this.dernier = EtatService.sain(Instant.EPOCH);
	}

	public EtatService etat() {
		return dernier;
	}

	@Scheduled(fixedDelayString = "${attenso.supervision.periode:60000}")
	public void controler() {
		Instant maintenant = horloge.instant();
		List<String> problemes = new ArrayList<>();

		try {
			jdbc.queryForObject("SELECT 1", Integer.class);
		}
		catch (RuntimeException panne) {
			problemes.add("base de données injoignable : " + panne.getMessage());
		}

		if (problemes.isEmpty()) {
			// Ces deux contrôles exigent la base : les tenter après un échec de
			// connexion produirait trois messages pour une seule panne.
			controlerFileDEnvoi(problemes);
			controlerOrdonnanceur(maintenant, problemes);
		}

		EtatService etat = new EtatService(!problemes.isEmpty(), List.copyOf(problemes),
				maintenant);
		publier(etat);
	}

	private void controlerFileDEnvoi(List<String> problemes) {
		Integer enAttente = jdbc.queryForObject("""
				SELECT count(*)::int FROM notification
				WHERE statut = 'EN_ATTENTE' AND cree_le < now() - interval '10 minutes'
				""", Integer.class);
		if (enAttente != null && enAttente > proprietes.smsEnAttenteMax()) {
			problemes.add(enAttente + " notification(s) en attente depuis plus de dix "
					+ "minutes : la file d'envoi est bloquée");
		}
	}

	/**
	 * L'ordonnanceur tourne-t-il ? On le déduit du journal des accès : la tâche
	 * de grâce y écrit {@code ABSENT_GRACE} quand elle agit.
	 *
	 * <p>Un jour sans aucun ticket ne prouve rien, et ce n'est pas une panne :
	 * on ne conclut que si des tickets appelés attendent une expiration qui
	 * n'arrive pas.
	 */
	private void controlerOrdonnanceur(Instant maintenant, List<String> problemes) {
		Integer graceEnRetard = jdbc.queryForObject("""
				SELECT count(*)::int FROM ticket
				WHERE statut = 'APPELE' AND grace_expire_le < ?
				""", Integer.class, java.sql.Timestamp.from(
						maintenant.minus(proprietes.tickToleranceMax())));
		if (graceEnRetard != null && graceEnRetard > 0) {
			problemes.add(graceEnRetard + " ticket(s) dont le délai de grâce a expiré "
					+ "il y a plus de " + proprietes.tickToleranceMax().toMinutes()
					+ " minutes sans être traités : l'ordonnanceur semble arrêté");
		}
	}

	/**
	 * Faut-il alerter ? Uniquement au <strong>changement</strong> d'état.
	 *
	 * <p>Pure, sans horloge ni base, parce que c'est la règle la plus facile à
	 * casser par inadvertance et la plus coûteuse à casser : répéter le
	 * message toutes les minutes pendant une panne de nuit fait couper les
	 * notifications, et la panne suivante passe alors inaperçue.
	 */
	public static boolean doitAlerter(EtatService avant, EtatService apres) {
		return avant.degrade() != apres.degrade();
	}

	private void publier(EtatService etat) {
		EtatService avantEtat = dernier;
		dernier = etat;
		if (!doitAlerter(avantEtat, etat)) {
			return;
		}
		if (etat.degrade()) {
			JOURNAL.error("SERVICE DÉGRADÉ : {}", String.join(" | ", etat.problemes()));
			alerter("Attenso — service dégradé : " + String.join(" | ", etat.problemes()));
		}
		else {
			JOURNAL.warn("service rétabli");
			alerter("Attenso — service rétabli");
		}
	}

	/**
	 * L'alerte externe ne doit jamais faire échouer la supervision.
	 *
	 * <p>Un webhook injoignable pendant une panne réseau est le cas NORMAL, pas
	 * l'exception : laisser remonter l'erreur tuerait la tâche planifiée, et on
	 * perdrait la supervision au moment précis où elle sert.
	 */
	private void alerter(String message) {
		if (!proprietes.alerteExterne()) {
			return;
		}
		try {
			http.post().uri(proprietes.webhook())
					.header("Content-Type", "application/json")
					.body("{\"text\":\"" + message.replace("\"", "'") + "\"}")
					.retrieve().toBodilessEntity();
		}
		catch (RuntimeException injoignable) {
			JOURNAL.warn("alerte non délivrée au webhook : {}", injoignable.getMessage());
		}
	}

	/** Depuis combien de temps l'état courant dure, pour l'affichage. */
	public Duration depuis() {
		return Duration.between(dernier.observeLe(), horloge.instant());
	}
}
