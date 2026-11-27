package ai.attenso.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Les tests datent dans le fuseau de la clinique, comme le produit.
 *
 * <p>Mesuré le 2026-11-27 : {@code main} est passé au rouge sur un commit de
 * fusion alors que la branche était verte vingt minutes plus tôt. Deux tests
 * d'intégration calculaient leur date par {@code LocalDate.now()}, donc dans le
 * fuseau de la MACHINE, pendant que le produit calcule la sienne dans celui de
 * la clinique — {@code LocalDate.now(horloge)}, Europe/Paris. Les runners
 * GitHub tournent en UTC : entre 23 h et minuit UTC, les deux ne sont pas le
 * même jour, le test réserve pour une date et le service en cherche une autre.
 *
 * <p>Une heure par jour où le CI est faux, et faux dans le sens dangereux :
 * rouge sans que le code soit en cause. Reproduit à volonté avec
 * {@code TZ=Pacific/Midway}, un fuseau qui a un jour de retard sur Paris.
 *
 * <p>{@code ConfigurationHorloge} porte déjà ce raisonnement pour le produit —
 * « en UTC, entre minuit et 1 h à Paris, ceux-là croiraient encore à la
 * veille ». Les tests ne l'avaient pas suivi.
 *
 * <p>{@code Instant.now()} n'est pas visé : un instant ne dépend d'aucun
 * fuseau. Seules les dates et les heures locales en dépendent.
 */
class JourDesTestsTest {

	private static final Path RACINE = Path.of("src/test/java");

	/** Ce fichier parle du motif ; il ne peut pas s'interdire de l'écrire. */
	private static final String MOI = "JourDesTestsTest.java";

	@Test
	@DisplayName("aucun test ne date dans le fuseau de la machine")
	void aucunTestNeDateDansLeFuseauDeLaMachine() throws IOException {
		List<String> nus = new ArrayList<>();
		try (Stream<Path> fichiers = Files.walk(RACINE)) {
			for (Path fichier : fichiers.filter(f -> f.toString().endsWith(".java")).toList()) {
				if (fichier.getFileName().toString().equals(MOI)) {
					continue;
				}
				String source = Files.readString(fichier);
				for (String motif : List.of("LocalDate.now()", "LocalDateTime.now()")) {
					int depuis = 0;
					while ((depuis = source.indexOf(motif, depuis)) != -1) {
						// Une mention en commentaire explique le piège ; elle ne le pose pas.
						if (!dansUnCommentaire(source, depuis)) {
							nus.add(fichier.getFileName() + " : " + motif);
						}
						depuis += motif.length();
					}
				}
			}
		}
		assertThat(nus)
				.as("ces tests datent dans le fuseau de la machine, pas dans celui "
						+ "de la clinique : ils échouent entre 23 h et minuit UTC. "
						+ "Utiliser aujourdhui() de SocleIntegration.")
				.isEmpty();
	}

	/** La ligne qui porte l'occurrence commence-t-elle par un commentaire ? */
	private static boolean dansUnCommentaire(String source, int position) {
		int debutDeLigne = source.lastIndexOf('\n', position) + 1;
		String avant = source.substring(debutDeLigne, position).strip();
		return avant.startsWith("//") || avant.startsWith("*");
	}

	/**
	 * La morsure. Sans elle, un balayage qui ne trouve plus aucun fichier —
	 * chemin changé, arborescence déplacée — passerait au vert en silence.
	 */
	@Test
	@DisplayName("le balayage voit bien les sources de test")
	void leBalayageVoitBienLesSourcesDeTest() throws IOException {
		try (Stream<Path> fichiers = Files.walk(RACINE)) {
			assertThat(fichiers.filter(f -> f.toString().endsWith(".java")).count())
					.as("aucune source de test balayée : garde vide")
					.isGreaterThan(20);
		}
	}
}
