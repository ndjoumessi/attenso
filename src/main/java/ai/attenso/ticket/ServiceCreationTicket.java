package ai.attenso.ticket;

import ai.attenso.config.ProprietesFile;
import ai.attenso.config.ProprietesHlr;
import ai.attenso.numero.BudgetHlrDao;
import ai.attenso.numero.ResultatVerification;
import ai.attenso.numero.VerificateurNumero;
import ai.attenso.numero.VerificationDao;
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

	private final VerificationDao verifications;

	private final BudgetHlrDao budget;

	private final ProprietesFile proprietes;

	private final ProprietesHlr proprietesHlr;

	public ServiceCreationTicket(InsertionTicket insertion, TicketRepository tickets,
			VerificateurNumero verificateur, VerificationDao verifications,
			BudgetHlrDao budget, ProprietesFile proprietes, ProprietesHlr proprietesHlr) {
		this.insertion = insertion;
		this.tickets = tickets;
		this.verificateur = verificateur;
		this.verifications = verifications;
		this.budget = budget;
		this.proprietes = proprietes;
		this.proprietesHlr = proprietesHlr;
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
		return avecReprise(tenant, jour, e164, verification.valide(), Priorisation.NORMALE, null,
				Origine.COMPTOIR);
	}

	/**
	 * Ticket saisi par l'accueil pour quelqu'un qui n'a pas de smartphone.
	 *
	 * <p>Sans lui, ces clients-là n'existent pas dans la file : l'ASV devrait
	 * les garder en tête, et le compteur de départs — donc l'argument de vente
	 * — ne les verrait jamais. Le numéro reste facultatif : un téléphone sans
	 * navigateur peut recevoir un SMS, pas de téléphone du tout en reçoit
	 * aucun, et les deux cas sont courants.
	 *
	 * <p>Il n'est jamais refusé pour un numéro injoignable, et il ne compte pas
	 * dans le numérateur du taux d'auto-enregistrement : il prouve justement
	 * que le client ne s'est pas enregistré seul.
	 */
	public Ticket creerPourClient(Tenant tenant, LocalDate jour, String telephoneBrut,
			UUID parUtilisateur) {
		return creerPourClient(tenant, jour, telephoneBrut, Priorisation.NORMALE, parUtilisateur);
	}

	/**
	 * Même geste, avec un motif de passage prioritaire choisi par l'accueil
	 * (EF-407).
	 *
	 * <p>À la différence d'une urgence, <strong>aucun SMS correctif ne
	 * part</strong>. EF-302 ne le prévoit que pour l'urgence, et c'est bien
	 * vu : une personne âgée qui passe devant décale la salle de quelques
	 * minutes, pas de la demi-heure qu'une urgence vitale impose. Prévenir
	 * tout le monde à chaque priorité coûterait un SMS par ticket prévenu
	 * pour une information que personne n'attend.
	 */
	public Ticket creerPourClient(Tenant tenant, LocalDate jour, String telephoneBrut,
			Priorisation priorisation, UUID parUtilisateur) {
		String e164 = vide(telephoneBrut) ? null
				: NumeroTelephone.normaliser(telephoneBrut).orElseThrow(NumeroInvalide::new);
		Boolean valide = e164 == null ? null : verifier(tenant, jour, e164).valide();
		return avecReprise(tenant, jour, e164, valide, priorisation, parUtilisateur,
				Origine.ACCUEIL);
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
		if (vide(telephoneBrut)) {
			return avecReprise(tenant, jour, null, null, Priorisation.URGENCE, parUtilisateur,
					Origine.ACCUEIL);
		}
		String e164 = NumeroTelephone.normaliser(telephoneBrut).orElseThrow(NumeroInvalide::new);
		return avecReprise(tenant, jour, e164, verifier(tenant, jour, e164).valide(),
				Priorisation.URGENCE, parUtilisateur, Origine.ACCUEIL);
	}

	/**
	 * Vérifie le numéro, avec cache et budget.
	 *
	 * <p><strong>Le budget se compte sur les vérifications, pas sur les
	 * tickets</strong>, et c'est la correction d'une erreur de la première
	 * version. Un numéro refusé ne crée aucun ticket : les garde-fous qui
	 * comptaient des tickets ne comptaient donc rien dans le seul cas qui
	 * coûte, et un numéro mort martelé donnait des appels payants à l'infini.
	 *
	 * <p>Le cache couvre les verdicts négatifs aussi, pour la même raison :
	 * repasser le même numéro refusé ne doit plus rien coûter, et n'apprend
	 * d'ailleurs rien de neuf à qui le tenterait.
	 *
	 * <p>Budget épuisé : on rend « indéterminé » sans appeler, et le ticket
	 * part quand même. Un plafond de dépense n'a pas à fermer la salle
	 * d'attente.
	 */
	private ResultatVerification verifier(Tenant tenant, LocalDate jour, String e164) {
		if (!verificateur.payant()) {
			return verificateur.verifier(e164);
		}
		Optional<ResultatVerification> connu = verifications.retrouver(tenant.id(), jour, e164);
		if (connu.isPresent()) {
			return connu.get();
		}
		// Réservation atomique : compter puis appeler laissait passer ensemble
		// toutes les requêtes ayant lu le compteur avant qu'aucune ne
		// l'incrémente.
		if (!budget.reserver(tenant.id(), jour, proprietesHlr.maxParJour())) {
			JOURNAL.warn("budget de {} vérifications atteint pour {} : les tickets "
					+ "continuent d'être émis, sans contrôle du numéro",
					proprietesHlr.maxParJour(), tenant.slug());
			return ResultatVerification.indetermine("budget de vérifications du jour atteint");
		}
		ResultatVerification resultat = verificateur.verifier(e164);
		verifications.enregistrer(tenant.id(), jour, e164, resultat);
		return resultat;
	}

	private static boolean vide(String texte) {
		return texte == null || texte.isBlank();
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
			Priorisation priorisation, UUID parUtilisateur, Origine origine) {
		DataIntegrityViolationException dernier = null;
		for (int essai = 1; essai <= TENTATIVES; essai++) {
			try {
				return insertion.inserer(tenant, jour, e164, valide, priorisation, parUtilisateur,
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
