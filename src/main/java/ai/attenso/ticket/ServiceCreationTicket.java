package ai.attenso.ticket;

import ai.attenso.config.ProprietesFile;
import ai.attenso.numero.ResultatVerification;
import ai.attenso.numero.VerificateurNumero;
import ai.attenso.numero.Verdict;
import ai.attenso.tenant.Tenant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Création d'un ticket, avec reprise sur conflit et vérification du numéro.
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

	private final TicketRepository tickets;

	private final VerificateurNumero verificateur;

	private final ProprietesFile proprietes;

	public ServiceCreationTicket(InsertionTicket insertion, TicketRepository tickets,
			VerificateurNumero verificateur, ProprietesFile proprietes) {
		this.insertion = insertion;
		this.tickets = tickets;
		this.verificateur = verificateur;
		this.proprietes = proprietes;
	}

	/**
	 * Auto-enregistrement au comptoir : le numéro est obligatoire, et un numéro
	 * dont l'opérateur confirme qu'il ne recevra rien est refusé — pendant que
	 * le client est encore là pour corriger sa faute de frappe.
	 */
	public Ticket creerDepuisQr(Tenant tenant, LocalDate jour, String telephoneBrut) {
		String e164 = NumeroTelephone.normaliser(telephoneBrut)
				.orElseThrow(NumeroInvalide::new);

		// Les garde-fous AVANT la vérification, qui est un appel payant vers un
		// service externe depuis un point d'entrée anonyme. Sans cet ordre, on
		// brûlerait des crédits sans même créer de ticket. Le contrôle
		// définitif reste celui d'InsertionTicket, dans la transaction.
		refuserSiPlafondAtteint(tenant, jour);
		refuserSiTropDeTickets(tenant, jour, e164);

		ResultatVerification verification = verifier(tenant, jour, e164);
		if (verification.verdict() == Verdict.INJOIGNABLE) {
			throw new NumeroInjoignable(verification.detail());
		}
		return avecReprise(tenant, jour, e164, verification.valide(), Priorite.NORMALE, null,
				Origine.COMPTOIR);
	}

	/**
	 * Urgence saisie par l'accueil : numéro facultatif, et jamais refusée.
	 *
	 * <p>Le verdict sert seulement à décider s'il faut payer un SMS. Bloquer
	 * une urgence parce qu'un opérateur déclare un numéro mort serait absurde :
	 * l'animal est là, le propriétaire aussi.
	 */
	public Ticket creerUrgence(Tenant tenant, LocalDate jour, String telephoneBrut,
			UUID parUtilisateur) {
		if (telephoneBrut == null || telephoneBrut.isBlank()) {
			return avecReprise(tenant, jour, null, null, Priorite.URGENCE, parUtilisateur,
					Origine.ACCUEIL);
		}
		String e164 = NumeroTelephone.normaliser(telephoneBrut).orElseThrow(NumeroInvalide::new);
		return avecReprise(tenant, jour, e164, verifier(tenant, jour, e164).valide(),
				Priorite.URGENCE, parUtilisateur, Origine.ACCUEIL);
	}

	/**
	 * Vérifie le numéro, ou réutilise un verdict déjà payé le même jour pour le
	 * même numéro — le cas de la famille avec deux animaux.
	 */
	private ResultatVerification verifier(Tenant tenant, LocalDate jour, String e164) {
		Optional<Ticket> dejaVu = tickets
				.findFirstByTenantIdAndJourAndTelephoneE164OrderByCreeLeDesc(tenant.id(), jour,
						e164);
		if (dejaVu.isPresent()) {
			Boolean connu = dejaVu.get().telephoneValide();
			JOURNAL.debug("verdict réutilisé pour un numéro déjà vu aujourd'hui");
			return new ResultatVerification(verdictDe(connu), "déjà vérifié aujourd'hui");
		}
		return verificateur.verifier(e164);
	}

	private Verdict verdictDe(Boolean valide) {
		if (Boolean.TRUE.equals(valide)) {
			return Verdict.JOIGNABLE;
		}
		return Boolean.FALSE.equals(valide) ? Verdict.INJOIGNABLE : Verdict.INDETERMINE;
	}

	private void refuserSiPlafondAtteint(Tenant tenant, LocalDate jour) {
		if (tickets.countByTenantIdAndJour(tenant.id(), jour)
				>= proprietes.ticketsMaxParJour()) {
			throw new PlafondJournalierAtteint(proprietes.ticketsMaxParJour());
		}
	}

	private void refuserSiTropDeTickets(Tenant tenant, LocalDate jour, String e164) {
		if (tickets.countByTenantIdAndJourAndTelephoneE164(tenant.id(), jour, e164)
				>= proprietes.ticketsMaxParNumero()) {
			throw new LimiteNumeroAtteinte(proprietes.ticketsMaxParNumero());
		}
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
