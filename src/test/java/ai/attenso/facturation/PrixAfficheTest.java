package ai.attenso.facturation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La vitrine écrit les prix en dur, et ce test est ce qui les empêche de
 * mentir.
 *
 * <p>Pourquoi en dur : la page d'accueil est la première qu'un vétérinaire
 * ouvre, souvent sur un réseau médiocre. Y ajouter un appel à l'API pour trois
 * nombres qui changent une fois par an imposerait un état de chargement et une
 * requête de plus à tout le monde, pour rien.
 *
 * <p>Pourquoi le test : {@link PalierTest} épingle la grille du §13, mais il ne
 * voit pas la vitrine. Sans ce test, une révision tarifaire laisserait la page
 * publique annoncer l'ancien prix — et c'est le genre d'erreur qu'on découvre
 * par un client mécontent, pas par une suite verte.
 */
class PrixAfficheTest {

	private static final Path VITRINE =
			Path.of("src/main/resources/static/app/vitrine.html");

	private String vitrine() throws IOException {
		return Files.readString(VITRINE, StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("la vitrine annonce le prix de chaque palier, au centime près")
	void laVitrineAnnonceLesPrixDesPaliers() throws IOException {
		String page = vitrine();
		for (Palier palier : Palier.values()) {
			// Les trois prix sont des euros ronds, et le resteront : le §13 ne
			// fixe aucun palier à centimes. Si cela changeait, ce test
			// échouerait ici plutôt que de laisser passer un arrondi muet.
			assertThat(palier.prixCentimes() % 100)
					.as("le palier %s n'est plus un euro rond : la vitrine doit "
							+ "afficher les centimes", palier)
					.isZero();
			assertThat(page)
					.as("la vitrine n'annonce pas le prix du palier %s", palier)
					.contains(palier.prixCentimes() / 100 + " €");
		}
	}

	@Test
	@DisplayName("elle n'annonce aucun prix qui ne corresponde à un palier")
	void elleNAnnonceAucunPrixInconnu() throws IOException {
		// Le pendant du test précédent : sans lui, un ancien prix laissé sur la
		// page passerait inaperçu tant que le nouveau y figure aussi.
		java.util.regex.Matcher prix = java.util.regex.Pattern
				.compile("(\\d+) €<small> / mois</small>").matcher(vitrine());
		java.util.List<Integer> affiches = new java.util.ArrayList<>();
		while (prix.find()) {
			affiches.add(Integer.parseInt(prix.group(1)));
		}
		assertThat(affiches)
				.as("les prix d'abonnement affichés sur la vitrine")
				.isNotEmpty()
				.containsExactlyInAnyOrder(java.util.Arrays.stream(Palier.values())
						.map(p -> p.prixCentimes() / 100).toArray(Integer[]::new));
	}
}
