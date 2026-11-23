package ai.attenso.web;

/** Limite de débit atteinte. Se traduit en HTTP 429. */
public class TropDeRequetes extends RuntimeException {

	public TropDeRequetes() {
		super("trop de requêtes, réessayez dans un instant");
	}
}
