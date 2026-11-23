package ai.attenso.numero;

/**
 * Vérifie qu'un numéro recevra bien un SMS, avant d'en payer un.
 *
 * <p>L'intérêt n'est pas d'économiser 4,5 centimes : c'est d'attraper la faute
 * de frappe <em>pendant que le client est encore au comptoir</em> et peut la
 * corriger. Un chiffre inversé, et il attend dans sa voiture un message qui ne
 * viendra jamais — exactement la promesse rompue que tout ce produit cherche à
 * éviter.
 */
public interface VerificateurNumero {

	ResultatVerification verifier(String telephoneE164);

	/**
	 * Cet appel coûte-t-il de l'argent ? Le budget quotidien et le cache ne
	 * s'appliquent qu'aux vérificateurs payants : les faire mordre sur un
	 * contrôle gratuit limiterait des enregistrements pour rien.
	 */
	default boolean payant() {
		return false;
	}
}
