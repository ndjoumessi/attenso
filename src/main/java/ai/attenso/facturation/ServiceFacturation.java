package ai.attenso.facturation;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Émission des factures mensuelles (EF-902, jalon L7).
 *
 * <p>Ce service <strong>ne prélève rien</strong> et ne parle à aucun
 * prestataire de paiement : il calcule et pose une facture. Encaisser est une
 * décision qui engage de l'argent réel et qui appartient à l'éditeur, pas à
 * une tâche planifiée.
 */
@Service
public class ServiceFacturation {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceFacturation.class);

	private final TenantRepository tenants;

	private final FacturationDao dao;

	private final Clock horloge;

	public ServiceFacturation(TenantRepository tenants, FacturationDao dao, Clock horloge) {
		this.tenants = tenants;
		this.dao = dao;
		this.horloge = horloge;
	}

	/**
	 * Facture le mois écoulé, le 1er à 4 h.
	 *
	 * <p>Après la purge RGPD de 3 h 30 : celle-ci efface des numéros de
	 * téléphone, pas des tickets, donc le compte de visites est intact. L'ordre
	 * est quand même fixé pour que deux tâches lourdes ne se croisent pas sur
	 * la même base.
	 */
	@Scheduled(cron = "${attenso.facturation.cron:0 0 4 1 * *}")
	public void facturerLeMoisEcoule() {
		LocalDate moisDernier = LocalDate.now(horloge).withDayOfMonth(1).minusMonths(1);
		for (Tenant tenant : tenants.findAllByActifTrue()) {
			try {
				ContexteTenant.executer(tenant.id(), () -> emettre(tenant, moisDernier));
			}
			catch (RuntimeException panne) {
				// Une clinique qui échoue ne doit pas empêcher les autres
				// d'être facturées : c'est un mois de chiffre d'affaires.
				JOURNAL.error("facturation de {} en échec pour {} : {}", tenant.slug(),
						moisDernier, panne.getMessage());
			}
		}
	}

	/**
	 * Émet la facture d'une période, ou ne fait rien si elle existe déjà.
	 *
	 * <p>Une clinique sans abonnement n'est pas facturée : c'est l'état du
	 * pilote, qui est gratuit. Ne pas confondre avec un abonnement à zéro euro,
	 * qui produirait des factures vides à archiver pendant dix ans.
	 */
	@Transactional
	public Optional<Facture> emettre(Tenant tenant, LocalDate premierJourDuMois) {
		Optional<Palier> palier = dao.palierAu(tenant.id(), premierJourDuMois);
		if (palier.isEmpty()) {
			return Optional.empty();
		}
		int visites = dao.visitesDuMois(tenant.id(), premierJourDuMois);
		BigDecimal coutSms = dao.coutSmsDuMois(tenant.id(), premierJourDuMois);
		Palier p = palier.get();

		Facture facture = new Facture(UUID.randomUUID(),
				numero(tenant, premierJourDuMois), premierJourDuMois, p, visites,
				p.visitesIncluses(), p.prixCentimes(), p.depassementCentimes(visites),
				p.totalCentimes(visites), coutSms, horloge.instant());

		if (!dao.emettre(facture, tenant.id())) {
			JOURNAL.debug("{} déjà facturé pour {}", tenant.slug(), premierJourDuMois);
			return dao.factures(tenant.id()).stream()
					.filter(f -> f.periode().equals(premierJourDuMois)).findFirst();
		}
		if (facture.margeCentimes().signum() < 0) {
			// Le signal que le §13 cherche : « SMS inclus » ne tient que si la
			// marge reste positive. Négative, c'est le palier qui est mal placé.
			JOURNAL.warn("marge NÉGATIVE pour {} en {} : {} centimes facturés, {} de SMS. "
					+ "Palier {} probablement trop bas pour {} visites.",
					tenant.slug(), premierJourDuMois, facture.totalCentimes(), coutSms,
					p, visites);
		}
		JOURNAL.info("facture {} émise pour {} : {} visites, {} centimes",
				facture.numero(), tenant.slug(), visites, facture.totalCentimes());
		return Optional.of(facture);
	}

	/**
	 * Le palier à conseiller, s'il y en a un.
	 *
	 * <p><strong>Les deux sens n'ont pas la même preuve.</strong> Une HAUSSE se
	 * conseille sur le mois en cours, parce que le dépassement est déjà payé :
	 * attendre la fin du mois ferait perdre de l'argent au client en silence.
	 * Une BAISSE, elle, ne se conseille que sur un mois <em>complet</em> — la
	 * conseiller le 2 du mois à une clinique qui n'a pas encore ouvert, comme
	 * le faisait la première version, c'est pousser vers une formule trop
	 * petite sur la foi d'un compteur à zéro.
	 *
	 * <p>Et jamais de baisse qui produirait du dépassement au volume observé :
	 * un conseil qui coûte plus cher que la situation actuelle n'est pas un
	 * conseil.
	 */
	public Optional<Palier> conseil(UUID tenantId, Palier actuel, LocalDate moisCourant) {
		if (actuel == null) {
			return Optional.empty();
		}
		int ceMois = dao.visitesDuMois(tenantId, moisCourant);
		if (actuel.depassementCentimes(ceMois) > 0) {
			Palier mieux = Palier.pour(ceMois);
			return mieux != actuel && mieux.totalCentimes(ceMois) < actuel.totalCentimes(ceMois)
					? Optional.of(mieux) : Optional.empty();
		}

		LocalDate moisPrecedent = moisCourant.minusMonths(1);
		int moisDernier = dao.visitesDuMois(tenantId, moisPrecedent);
		if (moisDernier == 0) {
			// Aucun mois complet derrière nous : rien à conseiller.
			return Optional.empty();
		}
		Palier plusPetit = Palier.pour(moisDernier);
		return plusPetit != actuel
				&& plusPetit.totalCentimes(moisDernier) < actuel.totalCentimes(moisDernier)
				? Optional.of(plusPetit) : Optional.empty();
	}

	public List<Facture> factures(UUID tenantId) {
		return dao.factures(tenantId);
	}

	/**
	 * Numéro lisible et stable : {@code slug-AAAA-MM}.
	 *
	 * <p>Pas de compteur global : il imposerait une séquence partagée entre
	 * cliniques, donc un point de contention et une fuite d'information — le
	 * numéro dirait à chaque client combien l'éditeur en a d'autres.
	 */
	private static String numero(Tenant tenant, LocalDate periode) {
		return "%s-%04d-%02d".formatted(tenant.slug(), periode.getYear(),
				periode.getMonthValue());
	}
}
