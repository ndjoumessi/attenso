package ai.attenso.ticket;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Le seul ordonnanceur du V0, et il ne fait qu'une chose : faire expirer la
 * grâce.
 *
 * <p>Tout le reste de la file avance sur événement, dans la transaction de la
 * transition. C'est délibéré : un ordonnanceur qui enverrait les SMS dériverait
 * au moindre retard, et un retard de SMS « revenez » est un client qui rate
 * son tour.
 *
 * <p>Le balayage est fait tenant par tenant parce que le délai de grâce est un
 * réglage de clinique, et parce que les politiques RLS exigent un tenant lié :
 * une requête sans tenant ne voit aucune ligne.
 */
@Component
public class TacheGrace {

	private static final Logger JOURNAL = LoggerFactory.getLogger(TacheGrace.class);

	private final TenantRepository tenants;

	private final TicketRepository tickets;

	private final ServiceFile file;

	private final Clock horloge;

	public TacheGrace(TenantRepository tenants, TicketRepository tickets, ServiceFile file,
			Clock horloge) {
		this.tenants = tenants;
		this.tickets = tickets;
		this.file = file;
		this.horloge = horloge;
	}

	@Scheduled(fixedDelayString = "${attenso.file.periode-tick}")
	public void tick() {
		for (Tenant tenant : tenants.findAllByActifTrue()) {
			try {
				ContexteTenant.executer(tenant.id(), () -> traiter(tenant));
			}
			catch (RuntimeException panne) {
				JOURNAL.error("tick de grâce en échec pour {} : {}", tenant.slug(),
						panne.getMessage());
			}
		}
	}

	private void traiter(Tenant tenant) {
		List<Ticket> expires = tickets.findByTenantIdAndStatutAndGraceExpireLeBefore(tenant.id(),
				Statut.APPELE, horloge.instant());
		for (Ticket expire : expires) {
			try {
				file.absent(tenant, expire.id());
				JOURNAL.info("grâce expirée : ticket n°{} de {} passe ABSENT",
						expire.numeroJour(), tenant.slug());
			}
			catch (TransitionInterdite | TicketInconnu concurrent) {
				// L'accueil a tranché entre-temps. C'est lui qui a raison.
				JOURNAL.debug("ticket {} déjà traité : {}", expire.id(),
						concurrent.getMessage());
			}
		}
	}
}
