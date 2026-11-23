package ai.attenso.ticket;

import ai.attenso.numero.VerificationDao;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purge RGPD du numéro de téléphone à J+1.
 *
 * <p>La ligne {@code ticket} reste, sans numéro : c'est elle qui porte le
 * compteur de départs, donc la preuve de valeur qu'on présente au gérant. On
 * efface la donnée personnelle, pas la statistique.
 */
@Component
public class TachePurge {

	private static final Logger JOURNAL = LoggerFactory.getLogger(TachePurge.class);

	private final TenantRepository tenants;

	private final TicketRepository tickets;

	private final VerificationDao verifications;

	private final Clock horloge;

	public TachePurge(TenantRepository tenants, TicketRepository tickets,
			VerificationDao verifications, Clock horloge) {
		this.tenants = tenants;
		this.tickets = tickets;
		this.verifications = verifications;
		this.horloge = horloge;
	}

	@Scheduled(cron = "${attenso.file.cron-purge:0 30 3 * * *}")
	public void purger() {
		for (Tenant tenant : tenants.findAllByActifTrue()) {
			LocalDate aujourdhui = LocalDate.ofInstant(horloge.instant(), tenant.zone());
			ContexteTenant.executer(tenant.id(), () -> purgerTenant(tenant, aujourdhui));
		}
	}

	@Transactional
	public void purgerTenant(Tenant tenant, LocalDate aujourdhui) {
		int purges = tickets.purgerTelephones(tenant.id(), aujourdhui);
		// La table des vérifications porte elle aussi des numéros : même règle,
		// et elle est entièrement effacée puisque son rôle est journalier.
		int verificationsPurgees = verifications.purger(tenant.id(), aujourdhui);
		if (purges > 0 || verificationsPurgees > 0) {
			JOURNAL.info("purge RGPD pour {} : {} numéros de tickets effacés, "
					+ "{} vérifications supprimées", tenant.slug(), purges,
					verificationsPurgees);
		}
	}
}
