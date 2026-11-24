package ai.attenso.supervision;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dit au démarrage si la base n'est pas la version majeure sur laquelle les
 * tests ont tourné.
 *
 * <p>Pourquoi ça mérite une veille au démarrage plutôt qu'un test : la version
 * de la base de production n'existe qu'à l'exécution. Aucun test ne peut la
 * lire, donc aucun test ne peut garder cet alignement. C'est exactement le
 * genre de divergence qui s'installe en silence — la CI reste verte, la
 * production tourne, et ce qui a été validé ne l'a pas été sur le moteur qui
 * garde les données. Les migrations et le cloisonnement par <em>row level
 * security</em> sont les deux choses qu'on ne rattrape pas après coup.
 *
 * <p>C'est un AVERTISSEMENT, pas un refus de démarrer. Une majeure inattendue
 * n'est pas une panne : l'application fonctionne probablement très bien. Faire
 * tomber le service d'une clinique pour un écart de version serait une panne
 * provoquée par la garde elle-même, ce qui est pire que ce qu'elle surveille.
 */
@Component
public class VeilleVersionBase implements ApplicationRunner {

	private static final Logger JOURNAL = LoggerFactory.getLogger(VeilleVersionBase.class);

	/** « 18.7 » et « 16.3 (Debian 16.3-1) » donnent tous deux leur majeure. */
	private static final Pattern MAJEURE = Pattern.compile("^(\\d{1,3})");

	/** Ce que la veille a constaté, exposé pour que le câblage soit testable. */
	public enum Verdict { CONCORDE, DIVERGE, ILLISIBLE }

	private final JdbcTemplate jdbc;

	private final int attendue;

	private Verdict verdict;

	private String versionLue;

	VeilleVersionBase(JdbcTemplate jdbc,
			@Value("${attenso.base.majeure-attendue}") int attendue) {
		this.jdbc = jdbc;
		this.attendue = attendue;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		versionLue = jdbc.queryForObject("SHOW server_version", String.class);
		verdict = verdictPour(versionLue, attendue);

		switch (verdict) {
			case CONCORDE -> JOURNAL.info(
					"PostgreSQL {} : même majeure que celle des tests", versionLue);
			case ILLISIBLE -> JOURNAL.warn(
					"version de PostgreSQL illisible (« {} ») : impossible de vérifier "
					+ "qu'elle correspond à celle des tests", versionLue);
			case DIVERGE -> JOURNAL.warn(
					"POSTGRESQL {} ALORS QUE LES TESTS TOURNENT SUR LA MAJEURE {} : les "
					+ "migrations et le cloisonnement par row level security ont été "
					+ "validés sur un autre moteur que celui qui garde les données. "
					+ "Aligner l'image de SocleIntegration, ou "
					+ "attenso.base.majeure-attendue si c'est la production qui a bougé.",
					versionLue, attendue);
		}
	}

	/**
	 * Le constat, isolé de tout branchement pour être éprouvable seul.
	 *
	 * @param version ce que renvoie {@code SHOW server_version}
	 * @param attendue la majeure sur laquelle les tests tournent
	 */
	static Verdict verdictPour(String version, int attendue) {
		if (version == null) {
			return Verdict.ILLISIBLE;
		}
		Matcher trouve = MAJEURE.matcher(version.trim());
		if (!trouve.find()) {
			return Verdict.ILLISIBLE;
		}
		return Integer.parseInt(trouve.group(1)) == attendue
				? Verdict.CONCORDE : Verdict.DIVERGE;
	}

	/** Ce que la veille a constaté au démarrage, ou null si elle n'a pas tourné. */
	public Verdict verdict() {
		return verdict;
	}

	/** La version lue, telle que la base l'annonce. */
	public String versionLue() {
		return versionLue;
	}
}
