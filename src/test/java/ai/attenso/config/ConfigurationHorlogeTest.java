package ai.attenso.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ce que ce test épingle : le jour de l'application est le jour de la clinique.
 * On ne compare pas une chaîne de zone, on interroge la règle : à un instant où
 * Paris et UTC ne sont pas le même jour, quelle date l'horloge donne-t-elle ?
 */
class ConfigurationHorlogeTest {

	@Test
	@DisplayName("entre minuit et 1h à Paris, « aujourd'hui » est déjà le lendemain d'UTC")
	void lAujourdHuiEstCeluiDeParis() {
		// 23:30 UTC le 24 novembre = 00:30 CET le 25 : les deux jours diffèrent.
		Instant nuit = Instant.parse("2026-11-24T23:30:00Z");
		var horloge = new ConfigurationHorloge().horloge();

		assertThat(LocalDate.ofInstant(nuit, horloge.getZone())).isEqualTo(LocalDate.of(2026, 11, 25));
	}

	@Test
	@DisplayName("en été, la fenêtre de divergence dure deux heures")
	void enEteLeDecalageEstDeDeuxHeures() {
		// 22:30 UTC le 24 juin = 00:30 CEST le 25 : déjà le lendemain à Paris.
		Instant nuit = Instant.parse("2026-06-24T22:30:00Z");
		var horloge = new ConfigurationHorloge().horloge();

		assertThat(LocalDate.ofInstant(nuit, horloge.getZone())).isEqualTo(LocalDate.of(2026, 6, 25));
	}
}
