package ai.attenso.carnet;

import ai.attenso.notification.FabriqueLien;
import ai.attenso.notification.ModeleMessage;
import ai.attenso.notification.ServiceNotification;
import ai.attenso.notification.TypeNotification;
import ai.attenso.securite.GenerateurReference;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import ai.attenso.ticket.NumeroInvalide;
import ai.attenso.ticket.NumeroTelephone;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carnet de santé et rappels de vaccins (EF-906).
 *
 * <p><strong>Donnée de santé animale, pas humaine.</strong> Le §3.4 du cahier
 * des charges met hors périmètre « toute donnée de santé humaine, et tout
 * hébergement HDS » : un carnet de vaccination de chat n'est ni l'un ni
 * l'autre. EC-05, qui dit « aucune donnée de santé » sans qualificatif,
 * contredit EF-906 : c'est le §3.4 qui tranche.
 *
 * <p><strong>La durée de conservation n'est pas celle de la file.</strong> Le
 * numéro d'un ticket est effacé à J+1 ; celui d'un carnet ne l'est pas, parce
 * qu'un rappel de vaccin se déclenche des mois plus tard. Deux finalités, deux
 * durées — c'est précisément ce que le RGPD demande d'articuler, et non une
 * entorse à la minimisation.
 */
@Service
public class ServiceCarnet {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceCarnet.class);

	/**
	 * On prévient trois semaines avant l'échéance.
	 *
	 * <p>Assez tôt pour prendre rendez-vous, assez tard pour ne pas être
	 * oublié. Un rappel le jour même arriverait après la date limite de
	 * protection de l'animal.
	 */
	public static final int JOURS_AVANT_RAPPEL = 21;

	private final CarnetDao dao;

	private final TenantRepository tenants;

	private final GenerateurReference jetons;

	private final ServiceNotification notifications;

	private final FabriqueLien liens;

	private final Clock horloge;

	public ServiceCarnet(CarnetDao dao, TenantRepository tenants, GenerateurReference jetons,
			ServiceNotification notifications, FabriqueLien liens, Clock horloge) {
		this.dao = dao;
		this.tenants = tenants;
		this.jetons = jetons;
		this.notifications = notifications;
		this.liens = liens;
		this.horloge = horloge;
	}

	@Transactional
	public Animal creer(UUID tenantId, String nom, String espece, String telephoneBrut,
			boolean avecCarnet) {
		String e164 = (telephoneBrut == null || telephoneBrut.isBlank()) ? null
				: NumeroTelephone.normaliser(telephoneBrut).orElseThrow(NumeroInvalide::new);
		String jeton = avecCarnet ? jetons.suivante() : null;
		UUID id = dao.creer(tenantId, nom, espece, e164, jeton, horloge.instant());
		return dao.parId(tenantId, id).orElseThrow();
	}

	/**
	 * Révoque le QR en cours et en émet un neuf (EF-906).
	 *
	 * @param reemettre {@code false} ferme le carnet au lieu d'en rouvrir un :
	 *        c'est ce que demande un propriétaire qui ne veut plus d'accès par
	 *        QR du tout, et le lui refuser viderait le mot « révocable » de
	 *        son sens.
	 */
	@Transactional
	public Animal revoquerLeCarnet(UUID tenantId, UUID animalId, boolean reemettre) {
		String nouveau = reemettre ? jetons.suivante() : null;
		dao.remplacerJeton(tenantId, animalId, nouveau, horloge.instant());
		JOURNAL.info("carnet de l'animal {} révoqué{}", animalId,
				reemettre ? " et réémis" : " et fermé");
		return dao.parId(tenantId, animalId).orElseThrow();
	}

	/** Les rappels de vaccins, chaque matin. */
	@Scheduled(cron = "${attenso.carnet.cron-rappel:0 30 9 * * *}")
	public void rappelerLesVaccins() {
		for (Tenant tenant : tenants.findAllByActifTrue()) {
			try {
				ContexteTenant.executer(tenant.id(), () -> rappelerPour(tenant));
			}
			catch (RuntimeException panne) {
				JOURNAL.error("rappels de vaccins en échec pour {} : {}", tenant.slug(),
						panne.getMessage());
			}
		}
	}

	@Transactional
	public int rappelerPour(Tenant tenant) {
		LocalDate limite = LocalDate.now(horloge.withZone(tenant.zone()))
				.plusDays(JOURS_AVANT_RAPPEL);
		List<CarnetDao.RappelDu> dus = dao.rappelsDus(tenant.id(), limite);
		int demandes = 0;
		for (CarnetDao.RappelDu du : dus) {
			// Marqué d'abord et sous condition : deux instances qui balayent
			// en même temps n'enverront qu'un seul SMS.
			if (!dao.marquerRappele(tenant.id(), du.vaccinationId(), horloge.instant())) {
				continue;
			}
			// Ni nom d'animal ni nom de vaccin dans le SMS : il s'affiche sur
			// un écran verrouillé, et le détail est derrière le lien, qui est
			// lui-même révocable.
			notifications.demanderPourVaccination(tenant, du.vaccinationId(),
					du.telephoneE164(), TypeNotification.RAPPEL_VACCIN,
					ModeleMessage.rappelVaccin(tenant.nom(),
							liens.pourCarnet(du.jetonCarnet())));
			demandes++;
		}
		if (demandes > 0) {
			JOURNAL.info("{} rappel(s) de vaccin demandé(s) pour {}", demandes, tenant.slug());
		}
		return demandes;
	}
}
