package ai.attenso.paiement;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ferme les cliniques dont l'abonnement résilié est arrivé à son terme.
 *
 * <p>La résiliation ne coupe rien le jour où elle est demandée : la clinique a
 * payé sa période et la garde jusqu'au dernier jour. Fermer tout de suite
 * reviendrait à encaisser un mois pour en servir trois semaines.
 *
 * <p>Tôt le matin, avant l'ouverture : une clinique ne doit pas voir son
 * service s'arrêter entre deux consultations.
 */
@Component
public class TacheFinAbonnement {

	private static final Logger JOURNAL = LoggerFactory.getLogger(TacheFinAbonnement.class);

	private final TenantRepository tenants;

	private final PaiementDao dao;

	private final Clock horloge;

	public TacheFinAbonnement(TenantRepository tenants, PaiementDao dao, Clock horloge) {
		this.tenants = tenants;
		this.dao = dao;
		this.horloge = horloge;
	}

	@Scheduled(cron = "${attenso.paiement.cron-fin:0 45 2 * * *}")
	public void fermerLesEchus() {
		LocalDate jour = LocalDate.now(horloge);
		for (Tenant tenant : tenants.findAllByActifTrue()) {
			try {
				boolean ferme = ContexteTenant.avec(tenant.id(),
						() -> dao.fermerSiEchu(tenant.id(), jour));
				if (ferme) {
					JOURNAL.info("clinique « {} » fermée : abonnement résilié arrivé à "
							+ "terme", tenant.slug());
				}
			}
			catch (RuntimeException panne) {
				// Une clinique en échec ne doit pas empêcher les suivantes
				// d'être traitées : même règle que pour la purge.
				JOURNAL.error("fin d'abonnement en échec pour {} : {}", tenant.slug(),
						panne.getMessage());
			}
		}
	}
}
