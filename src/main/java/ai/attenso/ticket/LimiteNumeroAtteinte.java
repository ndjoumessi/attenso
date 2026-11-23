package ai.attenso.ticket;

/**
 * Deux tickets par numéro et par jour : une famille avec deux animaux en a
 * besoin, au-delà c'est du bruit — ou un robot. Se traduit en HTTP 429.
 */
public class LimiteNumeroAtteinte extends RuntimeException {

	public LimiteNumeroAtteinte(int limite) {
		super("limite de " + limite + " tickets par numéro et par jour atteinte");
	}
}
