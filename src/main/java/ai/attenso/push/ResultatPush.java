package ai.attenso.push;

/**
 * @param abonnementMort l'opérateur de push dit que cet abonnement n'existe
 *                       plus. Le réessayer donnerait le même refus
 *                       indéfiniment : il faut le retirer, et le ticket
 *                       repassera au SMS.
 */
public record ResultatPush(boolean reussi, boolean abonnementMort, String erreur) {

	public static ResultatPush succes() {
		return new ResultatPush(true, false, null);
	}

	public static ResultatPush mort() {
		return new ResultatPush(false, true, "abonnement push expiré");
	}

	public static ResultatPush echec(String erreur) {
		return new ResultatPush(false, false, erreur);
	}
}
