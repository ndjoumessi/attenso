package ai.attenso.web;

/** Identifiants refusés. Un seul message pour les deux causes possibles. */
public class IdentifiantsRefuses extends RuntimeException {

	public IdentifiantsRefuses() {
		super("identifiants refusés");
	}
}
