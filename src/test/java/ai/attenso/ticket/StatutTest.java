package ai.attenso.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatutTest {

	private static final Instant MIDI = Instant.parse("2026-11-23T11:00:00Z");

	private Ticket neuf() {
		return new Ticket(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 11, 23), 1,
				"abcdefghijkl", Priorisation.NORMALE, 1_000_000L, "+33612345678", true,
				Origine.COMPTOIR, MIDI);
	}

	@Test
	@DisplayName("EN_CONSULTATION ne fait pas partie des états ouverts, et c'est "
			+ "ce qui rend la réévaluation nécessaire sur « présent »")
	void laConsultationQuitteLaFile() {
		assertThat(Statut.OUVERTS).containsExactlyInAnyOrder(Statut.EN_ATTENTE, Statut.PREVENU,
				Statut.APPELE, Statut.ABSENT);
		assertThat(Statut.EN_CONSULTATION.ouvert()).isFalse();
	}

	@Test
	@DisplayName("ABSENT et PARTI sont deux états distincts, et aucun ne mène à l'autre")
	void absentNEstPasParti() {
		assertThat(Statut.ABSENT.terminal()).isFalse();
		assertThat(Statut.PARTI.terminal()).isTrue();
		assertThat(Statut.ABSENT.peutAllerVers(Statut.PARTI)).isTrue();
		assertThat(Statut.PARTI.suivants()).isEmpty();
	}

	@Test
	@DisplayName("un ticket appelé puis absent peut être rappelé")
	void absentPuisRappele() {
		Ticket ticket = neuf();
		ticket.appeler(MIDI, Duration.ofMinutes(3));
		assertThat(ticket.graceExpireLe()).isEqualTo(MIDI.plus(Duration.ofMinutes(3)));

		ticket.absent();
		assertThat(ticket.statut()).isEqualTo(Statut.ABSENT);
		assertThat(ticket.graceExpireLe()).isNull();

		ticket.appeler(MIDI.plusSeconds(60), Duration.ofMinutes(3));
		assertThat(ticket.statut()).isEqualTo(Statut.APPELE);
	}

	@Test
	@DisplayName("un absent qui se présente entre en consultation sans qu'on "
			+ "ait à le rappeler")
	void lAbsentPeutEntrerDirectementEnConsultation() {
		Ticket ticket = neuf();
		ticket.appeler(MIDI, Duration.ofMinutes(3));
		ticket.absent();

		ticket.present(MIDI.plusSeconds(240));

		assertThat(ticket.statut()).isEqualTo(Statut.EN_CONSULTATION);
		assertThat(ticket.consultationLe()).isEqualTo(MIDI.plusSeconds(240));
		assertThat(ticket.appeleLe()).as("il avait bien été appelé").isEqualTo(MIDI);
	}

	@Test
	@DisplayName("le parcours normal clôt le ticket")
	void parcoursNormal() {
		Ticket ticket = neuf();
		ticket.appeler(MIDI, Duration.ofMinutes(3));
		ticket.present(MIDI.plusSeconds(30));
		assertThat(ticket.statut().ouvert()).isFalse();
		ticket.termine(MIDI.plusSeconds(1200));
		assertThat(ticket.closLe()).isEqualTo(MIDI.plusSeconds(1200));
	}

	@Test
	@DisplayName("une transition impossible est refusée par l'entité, pas par la base")
	void transitionRefusee() {
		Ticket ticket = neuf();
		assertThatThrownBy(() -> ticket.termine(MIDI))
				.isInstanceOf(TransitionInterdite.class)
				.hasMessageContaining("EN_ATTENTE");
		assertThat(ticket.statut()).isEqualTo(Statut.EN_ATTENTE);
	}

	@Test
	@DisplayName("un ticket terminé n'a plus aucune suite")
	void etatsTerminaux() {
		for (Statut terminal : Statut.TERMINAUX) {
			assertThat(terminal.suivants()).as("suites de %s", terminal).isEmpty();
		}
	}
}
