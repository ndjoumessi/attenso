package ai.attenso.numero;

/**
 * @param detail ce que l'opérateur a répondu, conservé pour le support : six
 *        semaines plus tard, « dead » et « absent_subscriber » n'appellent pas
 *        la même conversation avec le client.
 */
public record ResultatVerification(Verdict verdict, String detail) {

	public static ResultatVerification joignable(String detail) {
		return new ResultatVerification(Verdict.JOIGNABLE, detail);
	}

	public static ResultatVerification injoignable(String detail) {
		return new ResultatVerification(Verdict.INJOIGNABLE, detail);
	}

	public static ResultatVerification indetermine(String detail) {
		return new ResultatVerification(Verdict.INDETERMINE, detail);
	}

	/** Ce qu'on écrit dans {@code ticket.telephone_valide}. */
	public Boolean valide() {
		return switch (verdict) {
			case JOIGNABLE -> Boolean.TRUE;
			case INJOIGNABLE -> Boolean.FALSE;
			case INDETERMINE -> null;
		};
	}
}
