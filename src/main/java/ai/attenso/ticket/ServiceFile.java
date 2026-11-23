package ai.attenso.ticket;

import ai.attenso.journal.CommandeDao;
import ai.attenso.journal.JournalDao;
import ai.attenso.notification.ServiceNotification;
import ai.attenso.tenant.Tenant;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le moteur de la file.
 *
 * <p><strong>La réévaluation suit chaque transition, et c'est une correction
 * assumée de la spécification.</strong> La spec §4 ne la déclenchait que sur
 * {@code TERMINE}, {@code PARTI}, {@code ANNULE}, {@code ABSENT} et l'urgence.
 * Cette liste est à l'envers : le ticket quitte la file à
 * {@code EN_CONSULTATION}, pas à {@code TERMINE} — l'index partiel
 * {@code ticket_file} ne retient que quatre états ouverts. Donc {@code TERMINE}
 * ne déplace personne, alors que « présent », absent de la liste, remonte
 * toute la salle d'un cran, c'est-à-dire précisément l'instant où quelqu'un
 * franchit le seuil de prévenance.
 *
 * <p>Réévaluer après chaque transition coûte un {@code count} sur une quinzaine
 * de lignes et ne fait rien quand rien n'a bougé. En échange, la classe entière
 * des « on a oublié un déclencheur » disparaît — le même raisonnement qui a
 * mis le plafond de SMS dans la base plutôt que dans un {@code if}.
 */
@Service
public class ServiceFile {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceFile.class);

	private final TicketRepository tickets;

	private final ServiceNotification notifications;

	private final ServiceEstimation estimation;

	private final JournalDao journalAcces;

	private final CommandeDao commandes;

	private final ApplicationEventPublisher evenements;

	private final Clock horloge;

	public ServiceFile(TicketRepository tickets, ServiceNotification notifications,
			ServiceEstimation estimation, JournalDao journalAcces, CommandeDao commandes,
			ApplicationEventPublisher evenements, Clock horloge) {
		this.tickets = tickets;
		this.notifications = notifications;
		this.estimation = estimation;
		this.journalAcces = journalAcces;
		this.commandes = commandes;
		this.evenements = evenements;
		this.horloge = horloge;
	}

	// --- Lecture --------------------------------------------------------

	public LocalDate jourCourant(Tenant tenant) {
		return LocalDate.ofInstant(horloge.instant(), tenant.zone());
	}

	@Transactional(readOnly = true)
	public int position(Ticket ticket) {
		if (!ticket.statut().ouvert()) {
			return 0;
		}
		return (int) tickets.compterDevant(ticket.tenantId(), ticket.jour(), ticket.priorite(),
				ticket.ordre()) + 1;
	}

	@Transactional(readOnly = true)
	public List<LigneFile> tableauDuJour(UUID tenantId, LocalDate jour) {
		List<Ticket> tous = tickets.findByTenantIdAndJourOrderByPrioriteDescOrdreAsc(tenantId,
				jour);
		List<Ticket> ouverts = tous.stream().filter(t -> t.statut().ouvert()).toList();
		List<LigneFile> lignes = new ArrayList<>(tous.size());
		for (Ticket ticket : tous) {
			int position = ouverts.indexOf(ticket) + 1;
			lignes.add(new LigneFile(ticket.id(), ticket.numeroJour(), position, ticket.statut(),
					ticket.priorite(), ticket.creeLe(), ticket.appeleLe(),
					ticket.graceExpireLe(), ticket.joignable()));
		}
		return lignes;
	}

	/** Le compteur qui justifie le prix : les départs sans consultation. */
	@Transactional(readOnly = true)
	public long departsDuJour(UUID tenantId, LocalDate jour) {
		return tickets.countByTenantIdAndJourAndStatut(tenantId, jour, Statut.PARTI);
	}

	@Transactional(readOnly = true)
	public Ticket exiger(UUID tenantId, UUID ticketId) {
		return tickets.findByIdAndTenantId(ticketId, tenantId)
				.orElseThrow(() -> new TicketInconnu(ticketId));
	}

	// --- Transitions ----------------------------------------------------

	@Transactional
	public Ticket appeler(Tenant tenant, UUID ticketId, UUID parUtilisateur, UUID commandeId) {
		return appliquer(tenant, ticketId, parUtilisateur, commandeId, "APPELER",
				(t, maintenant) -> t.appeler(maintenant, tenant.delaiGrace()));
	}

	@Transactional
	public Ticket present(Tenant tenant, UUID ticketId, UUID parUtilisateur, UUID commandeId) {
		return appliquer(tenant, ticketId, parUtilisateur, commandeId, "PRESENT",
				Ticket::present);
	}

	@Transactional
	public Ticket termine(Tenant tenant, UUID ticketId, UUID parUtilisateur, UUID commandeId) {
		return appliquer(tenant, ticketId, parUtilisateur, commandeId, "TERMINE",
				Ticket::termine);
	}

	/**
	 * A renoncé et quitté les lieux. Jamais le même geste que « absent » :
	 * c'est ce chiffre-là qu'on présente en rendez-vous de vente.
	 */
	@Transactional
	public Ticket parti(Tenant tenant, UUID ticketId, UUID parUtilisateur, UUID commandeId) {
		return appliquer(tenant, ticketId, parUtilisateur, commandeId, "PARTI", Ticket::parti);
	}

	@Transactional
	public Ticket annule(Tenant tenant, UUID ticketId, UUID parUtilisateur, UUID commandeId) {
		return appliquer(tenant, ticketId, parUtilisateur, commandeId, "ANNULE",
				Ticket::annule);
	}

	/**
	 * Grâce expirée : appelé, pas là. Il reprend sa place juste après le
	 * suivant, et non en fin de file — il est dans le parking, pas parti.
	 */
	@Transactional
	public Ticket absent(Tenant tenant, UUID ticketId) {
		Ticket ticket = exiger(tenant.id(), ticketId);
		ticket.absent();
		reinsererApresLeSuivant(ticket);
		tickets.save(ticket);
		journalAcces.consigner(tenant.id(), null, ticket.id(), "ABSENT_GRACE", null);
		reevaluer(tenant, ticket.jour(), false);
		return ticket;
	}

	/** Insertion d'urgence : passe devant tout le monde, sans renumérotation. */
	@Transactional
	public void reevaluerApresUrgence(Tenant tenant, LocalDate jour) {
		reevaluer(tenant, jour, true);
	}

	/**
	 * Applique une transition, au plus une fois par commande.
	 *
	 * <p>L'enregistrement de la commande et la transition sont dans la même
	 * transaction : sans ça, un rejeu pourrait marquer une commande traitée
	 * sans l'avoir appliquée. Une commande déjà vue rend l'état courant sans
	 * rien changer — c'est ce qui rend le rejeu d'une panne réseau sûr.
	 */
	private Ticket appliquer(Tenant tenant, UUID ticketId, UUID parUtilisateur, UUID commandeId,
			String action, BiConsumer<Ticket, Instant> mutation) {
		Ticket ticket = exiger(tenant.id(), ticketId);
		if (commandeId != null
				&& !commandes.premiereFois(commandeId, tenant.id(), ticketId, action)) {
			JOURNAL.debug("commande {} déjà traitée, transition {} ignorée", commandeId, action);
			return ticket;
		}
		mutation.accept(ticket, horloge.instant());
		tickets.save(ticket);
		journalAcces.consigner(tenant.id(), parUtilisateur, ticket.id(), action, null);
		reevaluer(tenant, ticket.jour(), false);
		return ticket;
	}

	// --- Réévaluation ---------------------------------------------------

	void reevaluer(Tenant tenant, LocalDate jour, boolean apresUrgence) {
		reevaluer(tenant, jour, apresUrgence, Set.of());
	}

	/**
	 * Le seul émetteur de notifications. Appelé dans la transaction de la
	 * transition : le plafond de la base absorbe les doubles déclenchements.
	 *
	 * @param sansNotification tickets à promouvoir en {@code PREVENU} <em>sans</em>
	 *        envoyer « c'est presque à vous ». Sert au ticket qui vient d'être
	 *        créé : s'il arrive déjà sous le seuil, la personne est debout au
	 *        comptoir, et lui expédier « votre numéro est le 1 » puis « c'est
	 *        presque à vous » dans la même seconde est absurde à lire et payé
	 *        deux fois. Dans une clinique calme, c'est un SMS gâché à presque
	 *        chaque visite — soit la moitié du budget de deux SMS par visite.
	 */
	void reevaluer(Tenant tenant, LocalDate jour, boolean apresUrgence,
			Set<UUID> sansNotification) {
		List<Ticket> ouverts = tickets
				.findByTenantIdAndJourAndStatutInOrderByPrioriteDescOrdreAsc(tenant.id(), jour,
						Statut.OUVERTS);

		// Relevé AVANT promotion : un ticket promu PREVENU à l'instant ne doit
		// pas recevoir « revenez » et « +20 min » dans la même seconde. Le cas
		// ne peut d'ailleurs pas se produire — une urgence ne fait que reculer
		// les positions — mais l'écrire coûte une ligne et ferme la question.
		Set<UUID> dejaPrevenus = new HashSet<>();
		for (Ticket ticket : ouverts) {
			if (ticket.statut() == Statut.PREVENU) {
				dejaPrevenus.add(ticket.id());
			}
		}

		for (int rang = 0; rang < ouverts.size(); rang++) {
			Ticket ticket = ouverts.get(rang);
			int position = rang + 1;
			if (ticket.statut() == Statut.EN_ATTENTE && position <= tenant.seuilPrevenir()) {
				ticket.prevenir();
				tickets.save(ticket);
				if (!sansNotification.contains(ticket.id())) {
					notifications.demanderPrevenir(tenant, ticket);
				}
			}
		}

		if (apresUrgence) {
			long minutes = estimation.dureeMedianeMinutes(tenant.id());
			for (Ticket ticket : ouverts) {
				if (dejaPrevenus.contains(ticket.id())) {
					notifications.demanderCorrectif(tenant, ticket, minutes);
				}
			}
		}

		evenements.publishEvent(new EvenementFileModifiee(tenant.id(), jour));
	}

	/**
	 * Donne au ticket absent un {@code ordre} intermédiaire entre le premier et
	 * le deuxième de sa bande de priorité. L'écart laissé à la création
	 * ({@link Ticket#ECHELLE_ORDRE}) rend cette insertion possible une
	 * vingtaine de fois de suite avant épuisement.
	 */
	private void reinsererApresLeSuivant(Ticket absent) {
		List<Ticket> bande = tickets
				.findByTenantIdAndJourAndStatutInOrderByPrioriteDescOrdreAsc(absent.tenantId(),
						absent.jour(), Statut.OUVERTS)
				.stream()
				.filter(t -> !t.id().equals(absent.id()) && t.priorite() == absent.priorite())
				.toList();
		if (bande.isEmpty()) {
			return;
		}
		long premier = bande.get(0).ordre();
		long propose = (bande.size() == 1)
				? premier + Ticket.ECHELLE_ORDRE
				: premier + (bande.get(1).ordre() - premier) / 2;
		long nouvel = Math.max(propose, premier + 1);
		if (nouvel <= premier) {
			JOURNAL.warn("plus de place entre deux ordres pour le ticket {} : il garde sa place",
					absent.id());
			return;
		}
		absent.deplacer(nouvel);
	}
}
