package ai.attenso.inscription;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Le slug finit imprimé sur une affiche et dicté au téléphone. */
class SlugTest {

	@Test
	@DisplayName("les accents tombent, les espaces deviennent des tirets")
	void lesAccentsTombent() {
		assertThat(Slug.depuis("Clinique Vétérinaire des Cerisiers"))
				.isEqualTo("clinique-veterinaire-des-cerisiers");
	}

	@Test
	@DisplayName("la ponctuation ne laisse pas de tirets en trop")
	void laPonctuationNeLaissePasDeTiretsEnTrop() {
		assertThat(Slug.depuis("  Clinique  du  Parc — Dr. Martin & Associés  "))
				.isEqualTo("clinique-du-parc-dr-martin-associes");
	}

	@Test
	@DisplayName("un nom trop long est tronqué sans finir par un tiret")
	void unNomTropLongEstTronqueProprement() {
		String slug = Slug.depuis("Clinique Vétérinaire Intercommunale des Hauts "
				+ "Plateaux et de la Vallée Profonde");

		assertThat(slug).hasSizeLessThanOrEqualTo(Slug.LONGUEUR_MAX).doesNotEndWith("-");
	}

	@Test
	@DisplayName("un nom sans aucun caractère latin ne produit pas une URL vide")
	void unNomNonLatinNeProduitPasDUrlVide() {
		// « /t/ » n'est pas une URL : mieux vaut un slug generique, que
		// l'inscription dedoublonnera, qu'une route cassee imprimee sur une
		// affiche.
		assertThat(Slug.depuis("動物病院")).isEqualTo("clinique");
		assertThat(Slug.depuis("!!!")).isEqualTo("clinique");
	}
}
