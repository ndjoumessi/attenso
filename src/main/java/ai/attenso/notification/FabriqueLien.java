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

	/** Le lien d'un rendez-vous (EF-905) : consulter, et annuler. */
	public String pourRendezVous(String reference) {
		return proprietes.base() + "/r/" + reference;
	}

	/** Le lien du carnet de santé d'un animal (EF-906). */
	public String pourCarnet(String jeton) {
		return proprietes.base() + "/carnet/" + jeton;
	}
}
