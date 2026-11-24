package ai.attenso.mesure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Le chiffre qui décide du projet.
 *
 * <p>Le critère d'arrêt de la semaine 10 se lit ici : sous 30 %
 * d'auto-enregistrement, le cahier des charges demande d'arrêter. Une erreur
 * dans ce calcul ferait abandonner un produit qui marche, ou poursuivre un
 * produit qui ne marche pas.
 */
class MesureJourTest {

	private static final LocalDate JOUR = LocalDate.of(2026, 11, 24);

	private static MesureJour mesure(Integer arrivants, long comptoir, long accueil) {
		return new MesureJour(JOUR, arrivants, comptoir, accueil, 0);
	}

	@Test
	void le_taux_ne_compte_que_les_tickets_pris_par_le_client() {
		// Un ticket saisi par l'ASV prouve précisément que le client ne s'est
		// PAS enregistré seul : le compter gonflerait le taux et invaliderait
		// la décision.
		MesureJour mesure = mesure(40, 20, 15);

		assertThat(mesure.ticketsTotal()).isEqualTo(35);
		assertThat(mesure.tauxAutoEnregistrement()).isEqualTo(50);
	}

	@Test
	void un_denominateur_absent_ne_donne_pas_zero_pour_cent() {
		// C'est le piège central : rendre 0 % quand personne n'a compté les
		// arrivants ferait conclure à l'échec du pilote alors qu'il manque
		// seulement une saisie.
		MesureJour sansSaisie = mesure(null, 20, 0);

		assertThat(sansSaisie.tauxAutoEnregistrement()).isNull();
		assertThat(sansSaisie.zone()).isEqualTo("NON_MESURE");
	}

	@Test
	void zero_arrivant_saisi_ne_divise_pas_par_zero() {
		assertThat(mesure(0, 0, 0).tauxAutoEnregistrement()).isNull();
	}

	@Test
	void les_trois_zones_du_cahier_des_charges_sont_bornees_comme_ecrit() {
		assertThat(mesure(100, 29, 0).zone()).isEqualTo("INVALIDE");
		assertThat(mesure(100, 30, 0).zone()).isEqualTo("ZONE_GRISE");
		assertThat(mesure(100, 49, 0).zone()).isEqualTo("ZONE_GRISE");
		assertThat(mesure(100, 50, 0).zone()).isEqualTo("VALIDE");
	}

	@Test
	void le_taux_peut_depasser_cent_sans_etre_tronque() {
		// Deux animaux pour un seul propriétaire, ou un comptage d'arrivants
		// sous-estimé : le chiffre doit rester visible tel quel, pour que
		// l'anomalie se remarque au lieu d'être lissée.
		assertThat(mesure(10, 12, 0).tauxAutoEnregistrement()).isEqualTo(120);
	}
}
