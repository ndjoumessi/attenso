package ai.attenso.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.supervision.VeilleVersionBase.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le constat de la veille, et surtout l'alignement qu'elle surveille.
 *
 * <p>Le second test est le seul qui garde quelque chose d'utile en continu : la
 * veille compare la base à une valeur configurée, et cette valeur doit elle-même
 * correspondre à l'image sur laquelle les tests tournent. Sinon la garde
 * surveille un nombre qui ne veut rien dire.
 */
class VeilleVersionBaseTest {

	private static final Path SOCLE =
			Path.of("src/test/java/ai/attenso/integration/SocleIntegration.java");

	private static final Path PROPRIETES =
			Path.of("src/main/resources/application.properties");

	@Test
	@DisplayName("lit la majeure quelle que soit la forme annoncée par la base")
	void litLaMajeure() {
		assertThat(VeilleVersionBase.verdictPour("18.7", 18)).isEqualTo(Verdict.CONCORDE);
		assertThat(VeilleVersionBase.verdictPour("18", 18)).isEqualTo(Verdict.CONCORDE);
		assertThat(VeilleVersionBase.verdictPour("16.3 (Debian 16.3-1.pgdg120+1)", 16))
				.isEqualTo(Verdict.CONCORDE);
		assertThat(VeilleVersionBase.verdictPour("19beta1", 19)).isEqualTo(Verdict.CONCORDE);
	}

	@Test
	@DisplayName("diverge sur une majeure différente, pas sur une mineure")
	void divergeSurLaMajeure() {
		// Une mineure d'écart ne justifie pas d'alerter : c'est le lot normal
		// d'un hébergeur qui applique ses correctifs.
		assertThat(VeilleVersionBase.verdictPour("18.9", 18)).isEqualTo(Verdict.CONCORDE);
		assertThat(VeilleVersionBase.verdictPour("16.3", 18)).isEqualTo(Verdict.DIVERGE);
		assertThat(VeilleVersionBase.verdictPour("19.0", 18)).isEqualTo(Verdict.DIVERGE);
	}

	@Test
	@DisplayName("ne prétend pas conclure sur une version qu'elle ne sait pas lire")
	void illisible() {
		// Taire l'incertitude serait pire que l'avouer : un CONCORDE par défaut
		// ferait croire la garde en place alors qu'elle ne garde rien.
		assertThat(VeilleVersionBase.verdictPour(null, 18)).isEqualTo(Verdict.ILLISIBLE);
		assertThat(VeilleVersionBase.verdictPour("", 18)).isEqualTo(Verdict.ILLISIBLE);
		assertThat(VeilleVersionBase.verdictPour("inconnue", 18)).isEqualTo(Verdict.ILLISIBLE);
	}

	@Test
	@DisplayName("la majeure attendue est celle de l'image sur laquelle les tests tournent")
	void laValeurConfigureeSuitLImageDesTests() throws IOException {
		int image = majeureDeLImageDesTests();
		int configuree = majeureConfiguree();

		assertThat(configuree)
				.describedAs("attenso.base.majeure-attendue (%d) doit suivre l'image "
						+ "postgres:%d-alpine de SocleIntegration — sinon la veille "
						+ "compare la production à une majeure sur laquelle rien n'a "
						+ "été testé", configuree, image)
				.isEqualTo(image);
	}

	private static int majeureDeLImageDesTests() throws IOException {
		Matcher trouve = Pattern.compile("postgres:(\\d{1,3})(?:\\.\\d+)?-")
				.matcher(Files.readString(SOCLE));
		assertThat(trouve.find()).describedAs("image postgres introuvable dans %s", SOCLE)
				.isTrue();
		return Integer.parseInt(trouve.group(1));
	}

	private static int majeureConfiguree() throws IOException {
		Matcher trouve = Pattern.compile("(?m)^attenso\\.base\\.majeure-attendue=(\\d{1,3})\\s*$")
				.matcher(Files.readString(PROPRIETES));
		assertThat(trouve.find())
				.describedAs("attenso.base.majeure-attendue introuvable dans %s", PROPRIETES)
				.isTrue();
		return Integer.parseInt(trouve.group(1));
	}
}
