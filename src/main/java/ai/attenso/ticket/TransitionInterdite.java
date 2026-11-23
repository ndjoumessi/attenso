package ai.attenso.ticket;

import java.util.UUID;

/** Transition refusée par la machine à états. Se traduit en HTTP 409. */
public class TransitionInterdite extends RuntimeException {

	private final Statut depuis;

	private final Statut vers;

	public TransitionInterdite(UUID ticket, Statut depuis, Statut vers) {
		super("ticket %s : %s ne peut pas aller vers %s".formatted(ticket, depuis, vers));
		this.depuis = depuis;
		this.vers = vers;
	}

	public Statut depuis() {
		return depuis;
	}

	public Statut vers() {
		return vers;
	}
}
