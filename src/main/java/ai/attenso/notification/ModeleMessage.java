package ai.attenso.notification;

/**
 * Les corps de SMS.
 *
 * <p>Le nom de la clinique vient en tête de chaque message : le client
 * reconnaît son véto, pas une marque qu'il n'a jamais vue.
 *
 * <p><strong>Ne pas « corriger » les accents.</strong> Ces textes sont écrits
 * dans l'alphabet GSM 7 bits, où {@code é à è ù ç(Ç) ö ä} passent mais où
 * {@code ô ê î â û} ne passent pas. Un seul caractère hors alphabet fait
 * basculer le message entier en UCS-2 : la limite tombe de 160 à 70
 * caractères, et un message qu'on croit unitaire se facture double ou triple.
 * C'est pourquoi on lit « presque à vous » et non « bientôt à vous ».
 * {@link CompteurSegments} vérifie, et {@code ServiceNotification} journalise
 * en avertissement tout message qui dépasse un segment.
 */
public final class ModeleMessage {

	private ModeleMessage() {
	}

	public static String ticket(String clinique, int numero, String lien) {
		return "%s : votre numéro est le %d. Suivez votre tour : %s"
				.formatted(clinique, numero, lien);
	}

	public static String prevenir(String clinique, String lien) {
		return "%s : c'est presque à vous, merci de revenir. %s".formatted(clinique, lien);
	}

	public static String correctif(String clinique, long minutes, String lien) {
		return "%s : une urgence est passée avant vous, comptez environ %d min de plus. %s"
				.formatted(clinique, minutes, lien);
	}
}
