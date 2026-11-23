package ai.attenso.tenant;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Le nom affiché comme expéditeur d'un SMS.
 *
 * <p>Trois à onze caractères alphanumériques latins, sans accent ni espace :
 * c'est la règle des opérateurs français, reprise telle quelle par
 * l'agrégateur. Elle est vérifiée <strong>ici</strong> et pas seulement par la
 * base, pour une raison précise : l'API de l'agrégateur, plutôt que de refuser
 * un nom mal formé, le « corrige » en supprimant les caractères interdits.
 * Un SMS partirait donc sous un expéditeur que personne n'a enregistré, ce qui
 * est exactement ce qu'il ne faut pas quand l'expéditeur est l'identité de la
 * clinique.
 *
 * <p>Depuis le 1er mars 2026, ce nom doit en outre correspondre à une marque ou
 * une raison sociale réellement déclarée, avec autorisation écrite. Le code ne
 * peut pas le vérifier — seule la validation opérateur le fait — mais il peut
 * au moins garantir la forme.
 */
public final class NomExpediteur {

	public static final int LONGUEUR_MIN = 3;

	public static final int LONGUEUR_MAX = 11;

	private static final java.util.regex.Pattern FORME =
			java.util.regex.Pattern.compile("^[A-Za-z0-9]{3,11}$");

	private NomExpediteur() {
	}

	public static boolean valide(String nom) {
		return nom != null && FORME.matcher(nom).matches();
	}

	/**
	 * Proposition de nom à partir de la raison sociale, pour l'installation
	 * d'une clinique. <strong>Une proposition, pas une décision</strong> : le
	 * nom retenu doit être celui qui sera déposé auprès des opérateurs, et
	 * c'est un choix humain.
	 */
	public static String proposerDepuis(String raisonSociale) {
		if (raisonSociale == null) {
			return null;
		}
		String sansAccent = Normalizer.normalize(raisonSociale, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		String[] mots = sansAccent.split("[^A-Za-z0-9]+");
		// Le mot le plus distinctif plutôt que le premier : « Clinique » ne
		// distingue aucune clinique de sa voisine.
		String retenu = "";
		for (String mot : mots) {
			if (mot.length() > retenu.length() && !estGenerique(mot)) {
				retenu = mot;
			}
		}
		if (retenu.isEmpty()) {
			return null;
		}
		String propose = retenu.substring(0, Math.min(retenu.length(), LONGUEUR_MAX));
		return propose.length() >= LONGUEUR_MIN ? propose : null;
	}

	private static boolean estGenerique(String mot) {
		return switch (mot.toLowerCase(Locale.ROOT)) {
			case "clinique", "cabinet", "centre", "veterinaire", "veterinaires",
					"docteur", "dr", "des", "de", "du", "la", "le", "les", "sarl",
					"sas", "scp", "selarl" -> true;
			default -> false;
		};
	}
}
