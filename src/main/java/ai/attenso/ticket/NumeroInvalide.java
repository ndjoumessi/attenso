package ai.attenso.ticket;

/** Numéro non reconnu comme mobile français. Se traduit en HTTP 400. */
public class NumeroInvalide extends RuntimeException {

	public NumeroInvalide() {
		super("numéro de mobile français attendu");
	}
}
