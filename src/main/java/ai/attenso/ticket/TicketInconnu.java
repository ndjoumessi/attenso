package ai.attenso.ticket;

import java.util.UUID;

/** Ticket absent, ou appartenant à un autre tenant. Se traduit en HTTP 404. */
public class TicketInconnu extends RuntimeException {

	public TicketInconnu(UUID id) {
		super("ticket inconnu : " + id);
	}

	private TicketInconnu(String message) {
		super(message);
	}

	/**
	 * Le message ne répète pas la référence : elle vaut justificatif d'accès,
	 * et une référence recopiée dans un message d'erreur finit dans un journal.
	 */
	public static TicketInconnu parReference() {
		return new TicketInconnu("référence de suivi inconnue ou expirée");
	}
}
