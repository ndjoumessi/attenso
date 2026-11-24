package ai.attenso.equipe;

/** Cette adresse a déjà un compte dans cette clinique. */
public class AdresseDejaPrise extends RuntimeException {

	public AdresseDejaPrise() {
		super("cette adresse a déjà un accès dans cette clinique");
	}
}
