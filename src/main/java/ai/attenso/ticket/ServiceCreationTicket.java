package ai.attenso.ticket;

import ai.attenso.tenant.Tenant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Création d'un ticket, avec reprise sur conflit.
 *
 * <p>Il n'y a pas de table de compteurs : le numéro du jour est
 * {@code max + 1}, protégé par une contrainte d'unicité, et deux accueils qui
 * enregistrent dans la même seconde se départagent par une reprise. À une
 * insertion toutes les quelques minutes, la contention est nulle, et ça évite
 * une entité technique de plus.
 */
@Service
public class ServiceCreationTicket {

	private static final int TENTATIVES = 5;

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceCreationTicket.class);

	private final InsertionTicket insertion;

	public ServiceCreationTicket(InsertionTicket insertion) {
		this.insertion = insertion;
	}

	/** Auto-enregistrement au comptoir : le numéro est obligatoire. */
	public Ticket creerDepuisQr(Tenant tenant, LocalDate jour, String telephoneBrut) {
		String e164 = NumeroTelephone.normaliser(telephoneBrut)
				.orElseThrow(NumeroInvalide::new);
		return avecReprise(tenant, jour, e164, Boolean.TRUE, Priorite.NORMALE, null,
				Origine.COMPTOIR);
	}

	/** Urgence saisie par l'accueil : passe devant, numéro facultatif. */
	public Ticket creerUrgence(Tenant tenant, LocalDate jour, String telephoneBrut,
			UUID parUtilisateur) {
		String e164 = (telephoneBrut == null || telephoneBrut.isBlank()) ? null
				: NumeroTelephone.normaliser(telephoneBrut).orElseThrow(NumeroInvalide::new);
		return avecReprise(tenant, jour, e164, e164 == null ? null : Boolean.TRUE,
				Priorite.URGENCE, parUtilisateur, Origine.ACCUEIL);
	}

	private Ticket avecReprise(Tenant tenant, LocalDate jour, String e164, Boolean valide,
			short priorite, UUID parUtilisateur, Origine origine) {
		DataIntegrityViolationException dernier = null;
		for (int essai = 1; essai <= TENTATIVES; essai++) {
			try {
				return insertion.inserer(tenant, jour, e164, valide, priorite, parUtilisateur,
						origine);
			}
			catch (DataIntegrityViolationException conflit) {
				dernier = conflit;
				JOURNAL.debug("conflit de numéro du jour, tentative {}/{}", essai, TENTATIVES);
			}
		}
		throw dernier;
	}
}
