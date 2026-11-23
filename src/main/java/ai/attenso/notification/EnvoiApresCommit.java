package ai.attenso.notification;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import ai.attenso.ticket.EvenementFileModifiee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Vide la file d'envoi après commit, et jamais avant : un SMS payé pour une
 * transition qui a fini par être annulée est de l'argent jeté et un client
 * induit en erreur.
 *
 * <p>Le balayage périodique n'est pas un doublon : il rattrape les lignes
 * restées {@code EN_ATTENTE} après un redémarrage ou une panne de
 * l'agrégateur. La demande est en base, donc elle survit au processus.
 */
@Component
public class EnvoiApresCommit {

	private static final Logger JOURNAL = LoggerFactory.getLogger(EnvoiApresCommit.class);

	private final ServiceNotification notifications;

	private final TenantRepository tenants;

	public EnvoiApresCommit(ServiceNotification notifications, TenantRepository tenants) {
		this.notifications = notifications;
		this.tenants = tenants;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void surFileModifiee(EvenementFileModifiee evenement) {
		tenants.findById(evenement.tenantId()).ifPresent(this::envoyer);
	}

	@Scheduled(fixedDelayString = "${attenso.sms.periode-balayage:60000}")
	public void balayer() {
		tenants.findAllByActifTrue().forEach(this::envoyer);
	}

	private void envoyer(Tenant tenant) {
		try {
			ContexteTenant.executer(tenant.id(), () -> notifications.envoyerEnAttente(tenant));
		}
		catch (RuntimeException panne) {
			JOURNAL.error("envoi des notifications en échec pour {} : {}", tenant.slug(),
					panne.getMessage());
		}
	}
}
