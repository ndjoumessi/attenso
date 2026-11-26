package ai.attenso.agenda;

import ai.attenso.securite.GenerateurReference;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.tenant.TenantRepository;
import ai.attenso.ticket.NumeroInvalide;
import ai.attenso.ticket.NumeroTelephone;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'agenda : génération des créneaux et réservation (EF-905).
 *
 * <p><strong>L'agenda ne remplace pas la file.</strong> Le produit reste un
 * outil de flux libre : un rendez-vous réserve une place à une heure, et c'est
 * l'accueil qui crée le ticket quand la personne se présente. Vouloir faire
 * entrer automatiquement un rendez-vous dans la file à l'heure dite
 * produirait des tickets fantômes pour tous les clients en retard.
 */
@Service
public class ServiceAgenda {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceAgenda.class);

	/**
	 * Horizon de réservation. Six semaines : assez pour un rappel de vaccin,
	 * assez court pour qu'un changement d'horaires n'invalide pas un an de
	 * créneaux déjà matérialisés.
	 */
	public static final int JOURS_OUVERTS = 42;

	private final AgendaDao dao;

	private final TenantRepository tenants;

	private final GenerateurReference references;

	private final Clock horloge;

	public ServiceAgenda(AgendaDao dao, TenantRepository tenants,
			GenerateurReference references, Clock horloge) {
		this.dao = dao;
		this.tenants = tenants;
		this.references = references;
		this.horloge = horloge;
	}

	/** Matérialise les créneaux des six prochaines semaines, chaque nuit. */
	@Scheduled(cron = "${attenso.agenda.cron-generation:0 15 3 * * *}")
	public void genererLesCreneaux() {
		for (Tenant tenant : tenants.findAllByActifTrue()) {
			try {
				ContexteTenant.executer(tenant.id(), () -> genererPour(tenant));
			}
			catch (RuntimeException panne) {
				JOURNAL.error("génération des créneaux en échec pour {} : {}", tenant.slug(),
						panne.getMessage());
			}
		}
	}

	@Transactional
	public int genererPour(Tenant tenant) {
		return genererDepuis(tenant.id(), LocalDate.now(horloge.withZone(tenant.zone())));
	}

	/**
	 * Le même travail, à partir d'un jour DONNÉ.
	 *
	 * <p>La date est un paramètre et non une lecture de l'horloge, parce que
	 * l'amorçage de la démonstration sème sous une horloge fixée : il doit
	 * matérialiser les créneaux du jour qu'il sème, et non de celui où tourne
	 * la machine. {@link #genererPour} reste la porte de la tâche de nuit, qui
	 * a un locataire et pas de date.
	 */
	@Transactional
	public int genererDepuis(UUID tenantId, LocalDate depuis) {
		List<PlageHoraire> plages = dao.plages(tenantId);
		if (plages.isEmpty()) {
			return 0;
		}
		int produits = 0;
		for (int decalage = 0; decalage < JOURS_OUVERTS; decalage++) {
			LocalDate jour = depuis.plusDays(decalage);
			for (PlageHoraire plage : plages) {
				if (plage.jour() != jour.getDayOfWeek()) {
					continue;
				}
				for (LocalTime heure = plage.debut();
						!heure.plusMinutes(plage.dureeMinutes()).isAfter(plage.fin());
						heure = heure.plusMinutes(plage.dureeMinutes())) {
					dao.materialiser(tenantId, jour, heure, plage.places());
					produits++;
				}
			}
		}
		return produits;
	}

	/** Les créneaux encore réservables, à partir de maintenant. */
	public List<Creneau> disponibles(Tenant tenant, LocalDate debut, LocalDate fin) {
		return dao.creneaux(tenant.id(), debut, fin).stream()
				.filter(c -> !c.complet())
				.filter(c -> encoreReservable(tenant, c.jour(), c.heure()))
				.toList();
	}

	/**
	 * Un créneau de ce matin n'est pas réservable cet après-midi.
	 *
	 * <p>UNE seule règle, partagée par l'affichage et par l'écriture. Elle ne
	 * vivait que dans le filtre d'affichage, et c'était un défaut : rien
	 * n'empêchait de poster directement sur {@code /t/{slug}/rendez-vous} pour
	 * réserver une heure déjà passée. Un garde qui ne tient que dans la vue ne
	 * garde rien.
	 */
	public boolean encoreReservable(Tenant tenant, LocalDate jour, LocalTime heure) {
		LocalDate aujourdhui = LocalDate.now(horloge.withZone(tenant.zone()));
		LocalTime maintenant = LocalTime.now(horloge.withZone(tenant.zone()));
		return jour.isAfter(aujourdhui)
				|| (jour.equals(aujourdhui) && heure.isAfter(maintenant));
	}

	/**
	 * Réserve un créneau.
	 *
	 * @throws CreneauComplet si le quota est atteint. C'est le cas NORMAL d'une
	 *         réservation concurrente, pas une anomalie : deux personnes qui
	 *         cliquent sur la dernière place, la seconde reçoit ce refus.
	 */
	@Transactional
	public RendezVous reserver(Tenant tenant, LocalDate jour, LocalTime heure,
			String telephoneBrut, String motif) {
		String e164 = (telephoneBrut == null || telephoneBrut.isBlank()) ? null
				: NumeroTelephone.normaliser(telephoneBrut).orElseThrow(NumeroInvalide::new);

		// Vérifié AVANT de prendre la place : sinon un créneau passé consomme
		// une place le temps que la transaction s'annule.
		if (!encoreReservable(tenant, jour, heure)) {
			throw new CreneauPasse(jour, heure);
		}

		UUID creneauId = dao.reserverUnePlace(tenant.id(), jour, heure)
				.orElseThrow(() -> new CreneauComplet(jour, heure));

		UUID id = UUID.randomUUID();
		String reference = references.suivante();
		dao.poser(tenant.id(), id, creneauId, jour, heure, reference, e164, motif);
		// L'identifiant interne, JAMAIS la référence : celle-ci est le seul
		// justificatif du client — elle ouvre /api/rendez-vous/{reference} et
		// autorise l'annulation. Dans un journal, c'est une capacité d'annuler
		// le rendez-vous d'un inconnu. Aucun autre service de ce produit ne
		// journalise une référence.
		JOURNAL.info("rendez-vous {} pris pour {} le {} à {}", id, tenant.slug(), jour,
				heure);
		return new RendezVous(id, reference, jour, heure, e164, motif, "CONFIRME", null,
				horloge.instant());
	}

	/** Annule, et rend la place au créneau — sinon l'agenda se vide tout seul. */
	@Transactional
	public boolean annuler(Tenant tenant, String reference) {
		return dao.annuler(tenant.id(), reference)
				.map(creneauId -> {
					dao.rendreUnePlace(tenant.id(), creneauId);
					return true;
				})
				.orElse(false);
	}
}
