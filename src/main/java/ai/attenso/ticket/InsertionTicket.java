package ai.attenso.ticket;

import ai.attenso.config.ProprietesFile;
import ai.attenso.journal.JournalDao;
import ai.attenso.notification.ServiceNotification;
import ai.attenso.securite.GenerateurReference;
import ai.attenso.tenant.Tenant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'insertion elle-même, dans sa propre transaction.
 *
 * <p>Séparée de {@link ServiceCreationTicket} parce qu'un conflit sur
 * {@code (tenant_id, jour, numero_jour)} condamne la transaction : la reprise
 * doit se faire depuis l'extérieur, dans une transaction neuve. C'est ce qui
 * permet de se passer d'une table de compteurs.
 */
@Service
public class InsertionTicket {

	private static final int ESPACE_ORDRE_MAX = 1000;

	private final TicketRepository tickets;

	private final ServiceFile file;

	private final ServiceNotification notifications;

	private final GenerateurReference references;

	private final JournalDao journalAcces;

	private final ProprietesFile proprietes;

	private final Clock horloge;

	public InsertionTicket(TicketRepository tickets, ServiceFile file,
			ServiceNotification notifications, GenerateurReference references,
			JournalDao journalAcces, ProprietesFile proprietes, Clock horloge) {
		this.tickets = tickets;
		this.file = file;
		this.notifications = notifications;
		this.references = references;
		this.journalAcces = journalAcces;
		this.proprietes = proprietes;
		this.horloge = horloge;
	}

	@Transactional
	public Ticket inserer(Tenant tenant, LocalDate jour, String telephoneE164,
			Boolean telephoneValide, short priorite, UUID parUtilisateur) {
		// Plafond de dépense avant tout le reste : chaque ticket déclenche un
		// SMS payant et l'agrégateur n'en offre aucun. Compté dans la
		// transaction, donc deux enregistrements simultanés ne le franchissent
		// pas ensemble.
		if (tickets.countByTenantIdAndJour(tenant.id(), jour)
				>= proprietes.ticketsMaxParJour()) {
			throw new PlafondJournalierAtteint(proprietes.ticketsMaxParJour());
		}
		if (telephoneE164 != null) {
			long deja = tickets.countByTenantIdAndJourAndTelephoneE164(tenant.id(), jour,
					telephoneE164);
			if (deja >= proprietes.ticketsMaxParNumero()) {
				throw new LimiteNumeroAtteinte(proprietes.ticketsMaxParNumero());
			}
		}

		Ticket nouveau = new Ticket(UUID.randomUUID(), tenant.id(), jour,
				tickets.maxNumeroJour(tenant.id(), jour) + 1, references.suivante(), priorite,
				allouerOrdre(tenant.id(), jour), telephoneE164, telephoneValide,
				horloge.instant());

		// Deux choses dans cette ligne, et aucune n'est accessoire.
		//
		// saveAndFlush et non save : le conflit sur (tenant_id, jour,
		// numero_jour) doit remonter ICI, pas au commit, pour que la reprise de
		// ServiceCreationTicket sache quoi reprendre.
		//
		// Et on garde l'instance RENDUE : l'identifiant étant assigné, Spring
		// Data considère l'entité comme non neuve et passe par un merge, qui
		// rend une autre instance managée. Garder la copie d'origine donnait un
		// ticket détaché, dont l'état précédait la réévaluation — l'API
		// répondait EN_ATTENTE pour un ticket déjà passé PREVENU en base.
		Ticket ticket = tickets.saveAndFlush(nouveau);

		journalAcces.consigner(tenant.id(), parUtilisateur, ticket.id(), "CREATION", null);
		notifications.demanderTicket(tenant, ticket);
		// Le ticket qui vient d'être créé est promu sans SMS « revenez » : la
		// personne est au comptoir, et le SMS de ticket vient de partir.
		file.reevaluer(tenant, jour, priorite == Priorite.URGENCE, Set.of(ticket.id()));
		return ticket;
	}

	/**
	 * {@code ordre} = instant de création en millisecondes × l'échelle. L'écart
	 * laissé entre deux tickets est ce qui rend possible la réinsertion d'un
	 * absent. Le décalage d'une unité ne sert qu'aux créations dans la même
	 * milliseconde, ce qui n'arrive pas à une insertion toutes les minutes.
	 */
	private long allouerOrdre(UUID tenantId, LocalDate jour) {
		long base = horloge.millis() * Ticket.ECHELLE_ORDRE;
		for (int decalage = 0; decalage < ESPACE_ORDRE_MAX; decalage++) {
			if (!tickets.existsByTenantIdAndJourAndOrdre(tenantId, jour, base + decalage)) {
				return base + decalage;
			}
		}
		throw new IllegalStateException("aucun ordre libre autour de " + base);
	}
}
