package ai.attenso.notification;

/**
 * Compte les segments facturés d'un SMS.
 *
 * <p>Pourquoi ce calcul existe : le modèle économique du V0 tient à deux SMS
 * par visite. Un message de 161 caractères en coûte deux, et un seul caractère
 * hors de l'alphabet GSM 7 bits — un « ç » dans le nom d'une clinique, par
 * exemple — fait basculer tout le message en UCS-2, où la limite tombe de 160
 * à 70 caractères. Un message qu'on croit unitaire devient alors triple. Le
 * coût est donc calculé, pas supposé.
 */
public final class CompteurSegments {

	private static final String GSM7 =
			"@£$¥èéùìòÇ\nØø\rÅå"
			+ "Δ_ΦΓΛΩΠΨΣΘΞÆæ"
			+ "ßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
			+ "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
			+ "¿abcdefghijklmnopqrstuvwxyzäöñüà";

	/** Caractères GSM 7 bits qui comptent double (séquence d'échappement). */
	private static final String GSM7_ETENDU = "\f^{}\\[~]|€";

	private static final int GSM7_SIMPLE = 160;

	private static final int GSM7_CONCATENE = 153;

	private static final int UCS2_SIMPLE = 70;

	private static final int UCS2_CONCATENE = 67;

	private CompteurSegments() {
	}

	public static boolean tientEnGsm7(String texte) {
		return texte.codePoints()
				.allMatch(c -> GSM7.indexOf(c) >= 0 || GSM7_ETENDU.indexOf(c) >= 0);
	}

	public static int segments(String texte) {
		if (texte == null || texte.isEmpty()) {
			return 0;
		}
		if (tientEnGsm7(texte)) {
			int unites = texte.chars().map(c -> GSM7_ETENDU.indexOf(c) >= 0 ? 2 : 1).sum();
			return unites <= GSM7_SIMPLE ? 1 : divisionHaute(unites, GSM7_CONCATENE);
		}
		int unites = texte.length();
		return unites <= UCS2_SIMPLE ? 1 : divisionHaute(unites, UCS2_CONCATENE);
	}

	private static int divisionHaute(int numerateur, int denominateur) {
		return (numerateur + denominateur - 1) / denominateur;
	}
}
