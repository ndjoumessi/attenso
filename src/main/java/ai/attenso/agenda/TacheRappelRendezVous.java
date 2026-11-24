package ai.attenso.agenda;

import ai.attenso.notification.FabriqueLien;
import ai.attenso.notification.ModeleMessage;
import ai.attenso.notification.ServiceNotification;
import ai.attenso.notification.TypeNotification;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le rappel de la veille (EF-905).
 *
 * <p>À 18 h, et c'est un choix : un rappel envoyé à 8 h du matin pour le
 * lendemain se perd dans la journée, un rappel à 21 h réveille. 18 h attrape
 * les gens au moment où ils regardent leur agenda du lendemain.
 *
 * <p>Le marquage {@code rappel_envoye_le} est posé <strong>avant</strong> la
 * demande d'envoi et de façon conditionnelle : deux instances qui balayent en
 * même temps n'en enverront qu'un seul. C'est la même logique que le plafond
 * de SMS — l'unicité est tenue par la base, pas par un {@code if}.
 */
@Component
public class TacheRappelRendezVous {

	/** L'heure dans le SMS : « 14h30 » se lit mieux que « 14:30 » sur un écran basique. */
	private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("H'h'mm");

	private static final Logger JOURNAL = LoggerFactory.getLogger(TacheRappelRendezVous.class);

	private final TenantRepository tenants;

	private final AgendaDao dao;

	private final ServiceNotification notifications;

	private final Clock horloge;

	private final FabriqueLien liens;

	public TacheRappelRendezVous(TenantRepository tenants, AgendaDao dao,
			ServiceNotification notifications, Clock horloge, FabriqueLien liens) {
		this.tenants = tenants;
		this.dao = dao;
		this.notifications = notifications;
		this.horloge = horloge;
		this.liens = liens;
	}

	@Scheduled(cron = "${attenso.agenda.cron-rappel:0 0 18 * * *}")
	public void rappelerDemain() {
		for (Tenant tenant : tenants.findAllByActifTrue()) {
			try {
				ContexteTenant.executer(tenant.id(), () -> rappelerPour(tenant));
			}
			catch (RuntimeException panne) {
				JOURNAL.error("rappels J-1 en échec pour {} : {}", tenant.slug(),
						panne.getMessage());
			}
		}
	}

	@Transactional
	public int rappelerPour(Tenant tenant) {
		LocalDate demain = LocalDate.now(horloge.withZone(tenant.zone())).plusDays(1);
		int demandes = 0;
		for (RendezVous rdv : dao.aRappeler(tenant.id(), demain)) {
			// Marqué d'abord, et seulement si personne ne l'a fait : poser le
			// marquage après l'envoi laisserait passer un double rappel entre
			// deux instances.
			if (!dao.marquerRappele(tenant.id(), rdv.id(), horloge.instant())) {
				continue;
			}
			notifications.demanderPourRendezVous(tenant, rdv.id(), rdv.telephoneE164(),
					TypeNotification.RAPPEL_RDV,
					ModeleMessage.rappelRendezVous(tenant.nom(), HEURE.format(rdv.heure()),
							liens.pourRendezVous(rdv.reference())));
			demandes++;
		}
		if (demandes > 0) {
			JOURNAL.info("{} rappel(s) J-1 demandé(s) pour {}", demandes, tenant.slug());
		}
		return demandes;
	}
}
