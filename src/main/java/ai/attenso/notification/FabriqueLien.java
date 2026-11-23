package ai.attenso.notification;

import ai.attenso.config.ProprietesLien;
import ai.attenso.ticket.Ticket;
import org.springframework.stereotype.Component;

/** Le lien « suivez votre tour ». Court par conception. */
@Component
public class FabriqueLien {

	private final ProprietesLien proprietes;

	public FabriqueLien(ProprietesLien proprietes) {
		this.proprietes = proprietes;
	}

	public String pour(Ticket ticket) {
		return proprietes.base() + "/s/" + ticket.reference();
	}
}
