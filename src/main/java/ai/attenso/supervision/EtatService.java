package ai.attenso.supervision;

import java.time.Instant;
import java.util.List;

/**
 * @param degrade true dès qu'un contrôle échoue. C'est ce booléen qui déclenche
 *                l'alerte, et lui seul : alerter sur chaque contrôle produirait
 *                une volée de messages pour une seule panne.
 */
public record EtatService(boolean degrade, List<String> problemes, Instant observeLe) {

	public static EtatService sain(Instant quand) {
		return new EtatService(false, List.of(), quand);
	}
}
