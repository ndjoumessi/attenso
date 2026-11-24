package ai.attenso.inscription;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Fabrique le slug d'une clinique à partir de son nom (EF-901).
 *
 * <p>Le slug apparaît dans l'URL du QR imprimé au comptoir : il doit être
 * court, sans accent et lisible à voix haute au téléphone quand quelqu'un
 * dicte l'adresse. « Clinique Vétérinaire des Cerisiers » donne
 * {@code clinique-veterinaire-des-cerisiers}, tronqué si besoin.
 */
public final class Slug {

	/** Assez pour rester lisible, assez court pour tenir dans un QR dense. */
	public static final int LONGUEUR_MAX = 40;

	private Slug() {
	}

	public static String depuis(String nom) {
		String sansAccent = Normalizer.normalize(nom, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		String brut = sansAccent.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-+)|(-+$)", "");
		if (brut.length() > LONGUEUR_MAX) {
			brut = brut.substring(0, LONGUEUR_MAX).replaceAll("-+$", "");
		}
		// Un nom entièrement non latin (« 動物病院 ») ne laisse rien : on ne
		// rend pas une chaîne vide, qui produirait l'URL « /t/ ».
		return brut.isEmpty() ? "clinique" : brut;
	}
}
