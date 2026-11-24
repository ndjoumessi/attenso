package ai.attenso.editeur;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ce que la loi exige d'afficher, et ce qu'on n'exige pas à tort. */
class ProprietesEditeurTest {

	private static ProprietesEditeur complet() {
		return new ProprietesEditeur("Attenso SAS", "SAS", "1 000 €",
				"1 rue des Vétérinaires, 13000 Marseille", "12345678900011",
				"RCS Marseille 123 456 789", "FR12345678900", "Prénom Nom",
				"contact@attenso.fr", "+33 4 00 00 00 00", "Hébergeur",
				"3 rue de l'Hébergeur, 75000 Paris");
	}

	@Test
	@DisplayName("une identité complète ne manque de rien")
	void uneIdentiteCompleteNeManqueDeRien() {
		assertThat(complet().manquants()).isEmpty();
		assertThat(complet().complet()).isTrue();
	}

	@Test
	@DisplayName("un champ vide est signalé sous son nom de réglage")
	void unChampVideEstSignale() {
		ProprietesEditeur sansSiret = new ProprietesEditeur("Attenso SAS", "SAS", "1 000 €",
				"1 rue des Vétérinaires", "  ", "RCS", "TVA", "Prénom Nom",
				"contact@attenso.fr", null, "Hébergeur", "3 rue");

		// Le nom du réglage et non « SIRET » : le message sert à celui qui
		// remplit le fichier d'environnement, pas à un juriste.
		assertThat(sansSiret.manquants()).containsExactly("attenso.editeur.siret");
	}

	@Test
	@DisplayName("ni le capital ni la forme juridique ne sont exigés")
	void uneEntrepriseIndividuelleEstEnRegle() {
		// Un vétérinaire qui s'installe seul n'a ni capital ni forme sociétaire.
		// Les exiger rendrait le produit invendable à la moitié de sa cible.
		ProprietesEditeur individuelle = new ProprietesEditeur("Prénom Nom", null, null,
				"1 rue des Vétérinaires", "12345678900011", null, null, "Prénom Nom",
				"contact@attenso.fr", null, "Hébergeur", "3 rue");

		assertThat(individuelle.complet()).isTrue();
	}
}
