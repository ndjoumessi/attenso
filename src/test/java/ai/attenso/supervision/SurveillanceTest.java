package ai.attenso.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La règle d'alerte (EF-903).
 *
 * <p>Pourquoi cette règle est testée à part, et pas la détection elle-même :
 * fabriquer une panne d'ordonnanceur en test est impossible tant que
 * l'ordonnanceur tourne — il répare la situation en cinq secondes. C'est la
 * preuve que le détecteur ne se déclenche QUE sur une vraie panne, mais ça
 * rend le scénario intestable autrement qu'en arrêtant le planificateur.
 */
class SurveillanceTest {

	private static final Instant QUAND = Instant.parse("2026-11-24T08:00:00Z");

	private static EtatService degrade(String probleme) {
		return new EtatService(true, List.of(probleme), QUAND);
	}

	@Test
	@DisplayName("on alerte quand le service tombe")
	void onAlerteQuandLeServiceTombe() {
		assertThat(Surveillance.doitAlerter(EtatService.sain(QUAND),
				degrade("base injoignable"))).isTrue();
	}

	@Test
	@DisplayName("on alerte quand le service se rétablit")
	void onAlerteQuandLeServiceSeRetablit() {
		// Sans ce message, l'astreinte reste debout sur une panne déjà finie.
		assertThat(Surveillance.doitAlerter(degrade("base injoignable"),
				EtatService.sain(QUAND))).isTrue();
	}

	@Test
	@DisplayName("on n'alerte PAS à chaque contrôle pendant une panne qui dure")
	void onNAlertePasEnBoucle() {
		// Répéter le message toutes les minutes pendant une panne de nuit fait
		// couper les notifications, et la panne suivante passe inaperçue.
		assertThat(Surveillance.doitAlerter(degrade("base injoignable"),
				degrade("base injoignable"))).isFalse();
	}

	@Test
	@DisplayName("un problème qui en remplace un autre ne redéclenche pas d'alerte")
	void unProblemeQuiEnRemplaceUnAutreNAlertePas() {
		// Le service était déjà dégradé : c'est l'état qui compte pour
		// l'astreinte, pas le détail, qui est dans le journal.
		assertThat(Surveillance.doitAlerter(degrade("base injoignable"),
				degrade("file d'envoi bloquée"))).isFalse();
	}

	@Test
	@DisplayName("un service sain qui le reste ne dit rien")
	void unServiceSainNeDitRien() {
		assertThat(Surveillance.doitAlerter(EtatService.sain(QUAND),
				EtatService.sain(QUAND))).isFalse();
	}
}
