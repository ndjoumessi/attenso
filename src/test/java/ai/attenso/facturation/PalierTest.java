package ai.attenso.facturation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Les prix du §13, et l'arithmétique qui produit une facture lisible. */
class PalierTest {

	@Test
	@DisplayName("les trois paliers portent les prix du cahier des charges")
	void lesPrixSontCeuxDuCahierDesCharges() {
		assertThat(Palier.COMPTOIR.visitesIncluses()).isEqualTo(300);
		assertThat(Palier.COMPTOIR.prixCentimes()).isEqualTo(6900);
		assertThat(Palier.CLINIQUE.visitesIncluses()).isEqualTo(800);
		assertThat(Palier.CLINIQUE.prixCentimes()).isEqualTo(14900);
		assertThat(Palier.CENTRE.visitesIncluses()).isEqualTo(1500);
		assertThat(Palier.CENTRE.prixCentimes()).isEqualTo(23900);
	}

	@Test
	@DisplayName("sous le volume inclus, on ne paie que l'abonnement")
	void sousLeVolumeIlNYAPasDeDepassement() {
		assertThat(Palier.COMPTOIR.depassementCentimes(300)).isZero();
		assertThat(Palier.COMPTOIR.totalCentimes(120)).isEqualTo(6900);
	}

	@Test
	@DisplayName("le dépassement est de 15 centimes par visite au-delà du palier")
	void leDepassementEstAuTarifDuCahierDesCharges() {
		assertThat(Palier.COMPTOIR.depassementCentimes(310)).isEqualTo(150);
		assertThat(Palier.COMPTOIR.totalCentimes(310)).isEqualTo(7050);
	}

	@Test
	@DisplayName("les montants restent des entiers de centimes, jamais des flottants")
	void lesMontantsSontDesCentimesEntiers() {
		// 333 visites sur COMPTOIR : 33 x 15 = 495 centimes, soit 4,95 EUR pile.
		// En flottants, ce calcul produit regulierement 4,949999999999999.
		assertThat(Palier.COMPTOIR.totalCentimes(333)).isEqualTo(6900 + 495);
	}

	@Test
	@DisplayName("le palier conseillé est le moins cher qui couvre le volume")
	void lePalierConseilleEstLeMoinsCherQuiCouvre() {
		assertThat(Palier.pour(1)).isEqualTo(Palier.COMPTOIR);
		assertThat(Palier.pour(300)).isEqualTo(Palier.COMPTOIR);
		assertThat(Palier.pour(301)).isEqualTo(Palier.CLINIQUE);
		assertThat(Palier.pour(800)).isEqualTo(Palier.CLINIQUE);
		assertThat(Palier.pour(801)).isEqualTo(Palier.CENTRE);
	}

	@Test
	@DisplayName("au-delà du plus grand palier, c'est CENTRE plus dépassement")
	void auDelaDuPlusGrandPalierOnResteAuCentre() {
		// Il n'y a rien au-dessus : rendre null obligerait chaque appelant a
		// traiter un cas qui n'a pas de reponse commerciale.
		assertThat(Palier.pour(5000)).isEqualTo(Palier.CENTRE);
		assertThat(Palier.CENTRE.totalCentimes(2000)).isEqualTo(23900 + 500 * 15);
	}

	@Test
	@DisplayName("CONSTAT SUR LE §13 : le palier CLINIQUE n'est le moins cher à AUCUN volume")
	void lePalierCliniqueEstDomine() {
		// Avec les prix du cahier des charges, le dépassement à 0,15 € est si
		// bas que rester au COMPTOIR et payer le dépassement bat CLINIQUE
		// partout, et que CENTRE ne devient intéressant qu'au-delà de ~1 434
		// visites. CLINIQUE est donc un palier dominé : aucun client qui
		// compte n'a de raison de le prendre.
		//
		// Ce test ne corrige rien — il fige le constat pour qu'une révision
		// des prix le fasse échouer, et donc qu'on le revoie à ce moment-là.
		for (int visites = 1; visites <= 2000; visites++) {
			int comptoir = Palier.COMPTOIR.totalCentimes(visites);
			int clinique = Palier.CLINIQUE.totalCentimes(visites);
			int centre = Palier.CENTRE.totalCentimes(visites);
			assertThat(clinique)
					.as("à %d visites, CLINIQUE devrait être battu", visites)
					.isGreaterThan(Math.min(comptoir, centre));
		}
	}

	@Test
	@DisplayName("CONSTAT : CENTRE ne devient le moins cher qu'au-delà de 1 434 visites")
	void centreNeDevientInteressantQueTard() {
		assertThat(Palier.CENTRE.totalCentimes(1433))
				.isGreaterThan(Palier.COMPTOIR.totalCentimes(1433));
		assertThat(Palier.CENTRE.totalCentimes(1434))
				.isLessThanOrEqualTo(Palier.COMPTOIR.totalCentimes(1434));
	}
}
