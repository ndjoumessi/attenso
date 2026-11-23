package ai.attenso.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ces tests gardent la marge. Le modèle économique du V0 suppose deux SMS d'un
 * seul segment par visite ; si un message passe à deux segments, le coût double
 * sans que rien ne casse par ailleurs — donc sans que personne ne le voie.
 */
class CompteurSegmentsTest {

	private static final String CLINIQUE_LONGUE = "Clinique Veterinaire des Cerisiers";

	private static final String LIEN = "https://attenso.fr/s/aZ9k2Lm4Qp8x";

	@Test
	@DisplayName("les trois messages tiennent en un segment, même avec un nom "
			+ "de clinique long")
	void lesTroisModelesTiennentEnUnSegment() {
		assertThat(CompteurSegments.segments(
				ModeleMessage.ticket(CLINIQUE_LONGUE, 7, LIEN))).isEqualTo(1);
		assertThat(CompteurSegments.segments(
				ModeleMessage.prevenir(CLINIQUE_LONGUE, LIEN))).isEqualTo(1);
		assertThat(CompteurSegments.segments(
				ModeleMessage.correctif(CLINIQUE_LONGUE, 25, LIEN))).isEqualTo(1);
	}

	@Test
	@DisplayName("les trois messages restent dans l'alphabet GSM 7 bits")
	void lesTroisModelesRestentEnGsm7() {
		assertThat(CompteurSegments.tientEnGsm7(
				ModeleMessage.ticket(CLINIQUE_LONGUE, 7, LIEN))).isTrue();
		assertThat(CompteurSegments.tientEnGsm7(
				ModeleMessage.prevenir(CLINIQUE_LONGUE, LIEN))).isTrue();
		assertThat(CompteurSegments.tientEnGsm7(
				ModeleMessage.correctif(CLINIQUE_LONGUE, 25, LIEN))).isTrue();
	}

	@Test
	@DisplayName("é et à passent en GSM 7, ô et ê non — c'est la raison des "
			+ "formulations sans accent circonflexe")
	void lAlphabetGsm7EstIncomplet() {
		assertThat(CompteurSegments.tientEnGsm7("numéro à régler")).isTrue();
		assertThat(CompteurSegments.tientEnGsm7("bientôt")).isFalse();
		assertThat(CompteurSegments.tientEnGsm7("vous êtes")).isFalse();
		assertThat(CompteurSegments.tientEnGsm7("français")).isFalse();
	}

	@Test
	@DisplayName("un seul caractère hors alphabet fait tomber la capacité de 160 à 70")
	void unCaractereHorsAlphabetCoupeLaCapaciteEnDeux() {
		String centQuaranteGsm7 = "a".repeat(140);
		assertThat(CompteurSegments.segments(centQuaranteGsm7)).isEqualTo(1);
		assertThat(CompteurSegments.segments(centQuaranteGsm7 + "ô")).isEqualTo(3);
	}

	@Test
	@DisplayName("les bornes de segmentation")
	void bornes() {
		assertThat(CompteurSegments.segments("")).isZero();
		assertThat(CompteurSegments.segments("a".repeat(160))).isEqualTo(1);
		assertThat(CompteurSegments.segments("a".repeat(161))).isEqualTo(2);
		assertThat(CompteurSegments.segments("a".repeat(306))).isEqualTo(2);
		assertThat(CompteurSegments.segments("a".repeat(307))).isEqualTo(3);
		assertThat(CompteurSegments.segments("ô".repeat(70))).isEqualTo(1);
		assertThat(CompteurSegments.segments("ô".repeat(71))).isEqualTo(2);
	}

	@Test
	@DisplayName("les caractères échappés de GSM 7 comptent deux septets : "
			+ "80 euros tiennent juste, 81 débordent")
	void caracteresEchappes() {
		assertThat(CompteurSegments.segments("€".repeat(80))).isEqualTo(1);
		assertThat(CompteurSegments.segments("€".repeat(81))).isEqualTo(2);
		assertThat(CompteurSegments.tientEnGsm7("prix : 12€")).isTrue();
	}
}
