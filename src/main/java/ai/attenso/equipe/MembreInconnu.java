package ai.attenso.equipe;

/** Aucun compte de cette clinique ne porte cet identifiant. */
public class MembreInconnu extends RuntimeException {

	public MembreInconnu() {
		super("compte introuvable");
	}
}
