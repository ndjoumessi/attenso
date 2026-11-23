package ai.attenso.ticket;

import java.util.Optional;

/**
 * Normalisation et contrôle d'un numéro mobile français.
 *
 * <p>Ce contrôle n'est pas une validation de formulaire : un numéro mal saisi
 * est un SMS payé qui n'arrive pas, et un client qui attend un message qui ne
 * viendra jamais. La vérification HLR — l'opérateur confirme que la ligne
 * existe, pour 0,005 € — s'ajoutera ici quand le compte agrégateur sera ouvert.
 */
public final class NumeroTelephone {

	private NumeroTelephone() {
	}

	public static Optional<String> normaliser(String brut) {
		if (brut == null) {
			return Optional.empty();
		}
		String chiffres = brut.replaceAll("[\\s.\\-()]", "");
		if (chiffres.startsWith("+33")) {
			chiffres = "0" + chiffres.substring(3);
		}
		else if (chiffres.startsWith("0033")) {
			chiffres = "0" + chiffres.substring(4);
		}
		if (!chiffres.matches("0[67]\\d{8}")) {
			return Optional.empty();
		}
		return Optional.of("+33" + chiffres.substring(1));
	}
}
