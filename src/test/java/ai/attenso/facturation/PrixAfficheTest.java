package ai.attenso.facturation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La vitrine écrit la grille tarifaire en dur, et ce test est ce qui l'empêche
 * de mentir.
 *
 * <p>Pourquoi en dur : la page d'accueil est la première qu'un vétérinaire
 * ouvre, souvent sur un réseau médiocre. Y ajouter un appel à l'API pour trois
 * noms, trois prix et trois volumes qui changent une fois par an imposerait un
 * état de chargement et une requête de plus à tout le monde.
 *
 * <p>Pourquoi le test : {@link PalierTest} épingle la grille du §13, mais il ne
 * voit pas la vitrine. Et ce n'est pas théorique — la première version de ce
 * test ne vérifiait QUE les prix, qui étaient justes ; elle est passée au vert
 * alors que la page annonçait « Cabinet » pour le palier Comptoir, « jusqu'à
 * 400 visites » au lieu de 300 et « 1 000 » au lieu de 800. Une clinique
 * pouvait s'abonner sur une promesse de 400 visites et recevoir du dépassement
 * dès la 301<sup>e</sup>. D'où la vérification des TROIS colonnes.
 */
class PrixAfficheTest {

	private static final Path VITRINE =
			Path.of("src/main/resources/static/app/vitrine.html");

	/**
	 * Les espaces de groupement des milliers sont insécables ou fines selon qui
	 * écrit la page ; les comparer à l'octet rendrait ce test cassant pour une
	 * raison qui n'intéresse personne.
	 */
	private static String sansEspaces(String texte) {
		return texte.replaceAll("[\\s\\u00a0\\u202f]", "");
	}

	private String vitrine() throws IOException {
		return Files.readString(VITRINE, StandardCharsets.UTF_8);
	}

	/** « COMPTOIR » → « Comptoir », le nom tel que le §13 l'écrit. */
	private static String nomAffiche(Palier palier) {
		String nom = palier.name();
		return nom.charAt(0) + nom.substring(1).toLowerCase();
	}

	@Test
	@DisplayName("la vitrine annonce le nom, le prix et le volume de chaque palier")
	void laVitrineAnnonceChaquePalier() throws IOException {
		String page = vitrine();
		String compacte = sansEspaces(page);
		for (Palier palier : Palier.values()) {
			assertThat(page)
					.as("la vitrine n'annonce pas le palier %s sous son nom", palier)
					.contains(">" + nomAffiche(palier) + "<");

			// Les trois prix sont des euros ronds et le resteront : le §13 n'en
			// fixe aucun au centime. Si cela changeait, échouer ici valait mieux
			// que d'afficher un arrondi muet.
			assertThat(palier.prixCentimes() % 100)
					.as("le palier %s n'est plus un euro rond : la vitrine doit "
							+ "afficher les centimes", palier)
					.isZero();
			assertThat(compacte)
					.as("la vitrine n'annonce pas le prix du palier %s", palier)
					.contains(sansEspaces(palier.prixCentimes() / 100 + " €"));

			assertThat(compacte)
					.as("la vitrine n'annonce pas le volume inclus du palier %s", palier)
					.contains(sansEspaces(palier.visitesIncluses() + " visites"));
		}
	}

	@Test
	@DisplayName("elle annonce le tarif du dépassement, qui s'applique aux trois")
	void elleAnnonceLeDepassement() throws IOException {
		// Il ne dépend d'aucun palier mais c'est un PRIX affiché : une clinique
		// qui déborde le paie, et le découvrir sur la facture serait le pire
		// moment. Écrit « 0,15 € » à la française, donc on compare sur la
		// virgule plutôt que sur un point.
		String attendu = String.format("%d,%02d €", Palier.DEPASSEMENT_CENTIMES / 100,
				Palier.DEPASSEMENT_CENTIMES % 100);
		assertThat(sansEspaces(vitrine()))
				.as("la vitrine n'annonce pas le tarif de dépassement")
				.contains(sansEspaces(attendu));
	}

	@Test
	@DisplayName("elle n'annonce ni prix ni volume qui ne corresponde à un palier")
	void elleNAnnonceRienQuiNeCorrespondeAUnPalier() throws IOException {
		// Le pendant du test précédent : sans lui, une ancienne valeur laissée
		// sur la page passerait inaperçue tant que la nouvelle y figure aussi.
		assertThat(nombresDe("(\\d+) €<small> / mois</small>"))
				.as("les prix d'abonnement affichés sur la vitrine")
				.isNotEmpty()
				.containsExactlyInAnyOrder(Arrays.stream(Palier.values())
						.map(p -> p.prixCentimes() / 100).toArray(Integer[]::new));

		assertThat(nombresDe("Jusqu'à ([\\d\\s\\u00a0\\u202f]+) visites par mois"))
				.as("les volumes inclus affichés sur la vitrine")
				.isNotEmpty()
				.containsExactlyInAnyOrder(Arrays.stream(Palier.values())
						.map(Palier::visitesIncluses).toArray(Integer[]::new));
	}

	private List<Integer> nombresDe(String motif) throws IOException {
		Matcher trouve = Pattern.compile(motif).matcher(vitrine());
		List<Integer> valeurs = new ArrayList<>();
		while (trouve.find()) {
			valeurs.add(Integer.parseInt(sansEspaces(trouve.group(1))));
		}
		return valeurs;
	}
}
