package ai.attenso.web;

import ai.attenso.agenda.AgendaDao;
import ai.attenso.agenda.Creneau;
import ai.attenso.agenda.PlageHoraire;
import ai.attenso.agenda.RendezVous;
import ai.attenso.agenda.ServiceAgenda;
import ai.attenso.securite.PrincipalPersonnel;
import ai.attenso.ticket.ResolveurReference;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.ServiceFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agenda et réservation en ligne (EF-905).
 *
 * <p>Deux publics et deux justificatifs, comme partout dans ce système : le
 * client passe par le slug public puis par la référence opaque de son
 * rendez-vous ; l'accueil passe par son jeton.
 */
@RestController
public class ControleurAgenda {

	/**
	 * Plus serré que l'enregistrement d'un ticket : une réservation engage un
	 * créneau pour des semaines, et un script qui en prend cinquante bloque la
	 * clinique bien plus durablement qu'une file saturée d'un jour.
	 */
	private static final int RESERVATIONS_MAX = 5;

	private static final Duration FENETRE = Duration.ofMinutes(30);

	/** Quatre semaines affichées par défaut : au-delà, la page devient illisible. */
	private static final int JOURS_AFFICHES = 28;

	private final ServiceTenant cliniques;

	private final ServiceAgenda agenda;

	private final AgendaDao dao;

	private final ServiceFile file;

	private final LimiteurDebit limiteur;

	private final AdresseAppelant adresses;

	private final ResolveurReference resolveur;

	public ControleurAgenda(ServiceTenant cliniques, ServiceAgenda agenda, AgendaDao dao,
			ServiceFile file, LimiteurDebit limiteur, AdresseAppelant adresses,
			ResolveurReference resolveur) {
		this.cliniques = cliniques;
		this.agenda = agenda;
		this.dao = dao;
		this.file = file;
		this.limiteur = limiteur;
		this.adresses = adresses;
		this.resolveur = resolveur;
	}

	// --- Côté client ----------------------------------------------------

	@GetMapping("/t/{slug}/creneaux")
	public List<VueCreneau> creneaux(@PathVariable String slug,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate debut) {
		Tenant clinique = cliniques.parSlug(slug);
		LocalDate depuis = debut != null ? debut : file.jourCourant(clinique);
		return ContexteTenant.avec(clinique.id(),
				() -> agenda.disponibles(clinique, depuis, depuis.plusDays(JOURS_AFFICHES))
						.stream()
						.map(c -> new VueCreneau(c.jour(), c.heure(), c.restantes()))
						.toList());
	}

	@PostMapping("/t/{slug}/rendez-vous")
	@ResponseStatus(HttpStatus.CREATED)
	public VueRendezVous reserver(@PathVariable String slug,
			@Valid @RequestBody DemandeRendezVous demande, HttpServletRequest requete) {
		if (!limiteur.autoriser("rdv:" + adresses.de(requete), RESERVATIONS_MAX, FENETRE)) {
			throw new TropDeRequetes();
		}
		Tenant clinique = cliniques.parSlug(slug);
		return ContexteTenant.avec(clinique.id(), () -> vue(clinique,
				agenda.reserver(clinique, demande.jour(), demande.heure(),
						demande.telephone(), demande.motif())));
	}

	@GetMapping("/api/rendez-vous/{reference}")
	public VueRendezVous consulter(@PathVariable String reference) {
		return avecReference(reference, (clinique, rdv) -> vue(clinique, rdv));
	}

	/**
	 * Annulation par le client (EF-905).
	 *
	 * <p>Rendre la place au créneau est la moitié du geste : sans ça, l'agenda
	 * se viderait tout seul au fil des annulations, et la clinique refuserait
	 * des clients devant des créneaux qu'elle croit pleins.
	 */
	@PostMapping("/api/rendez-vous/{reference}/annuler")
	public VueRendezVous annuler(@PathVariable String reference) {
		return avecReference(reference, (clinique, rdv) -> {
			agenda.annuler(clinique, reference);
			return vue(clinique, dao.parReference(clinique.id(), reference).orElse(rdv));
		});
	}

	// --- Côté accueil ---------------------------------------------------

	@GetMapping("/api/agenda")
	public List<VueRendezVous> journee(@AuthenticationPrincipal PrincipalPersonnel principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate jour) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		LocalDate quand = jour != null ? jour : file.jourCourant(clinique);
		return dao.duJour(clinique.id(), quand).stream()
				.map(rdv -> vue(clinique, rdv)).toList();
	}

	@GetMapping("/api/agenda/plages")
	public List<PlageHoraire> plages(@AuthenticationPrincipal PrincipalPersonnel principal) {
		return dao.plages(principal.tenantId());
	}

	/**
	 * Déclare une tranche d'ouverture, puis matérialise immédiatement les
	 * créneaux : sans ça, la clinique ne verrait son agenda qu'au lendemain
	 * matin, après la tâche de nuit, et croirait le réglage perdu.
	 */
	@PostMapping("/api/agenda/plages")
	@ResponseStatus(HttpStatus.CREATED)
	public List<PlageHoraire> ajouterPlage(
			@AuthenticationPrincipal PrincipalPersonnel principal,
			@Valid @RequestBody DemandePlage demande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		dao.ajouterPlage(clinique.id(), DayOfWeek.of(demande.jourSemaine()),
				demande.debut(), demande.fin(), demande.dureeMinutes(), demande.places());
		agenda.genererPour(clinique);
		return dao.plages(clinique.id());
	}

	// --- Communs --------------------------------------------------------

	/**
	 * Résout la clinique d'une référence, puis agit sous son cloisonnement.
	 *
	 * <p>Par la fonction {@code SECURITY DEFINER} et non par un balayage des
	 * cliniques : balayer dirait à l'appelant, par le temps de réponse,
	 * combien de cliniques existent et où se trouve la sienne.
	 */
	private <T> T avecReference(String reference,
			java.util.function.BiFunction<Tenant, RendezVous, T> action) {
		Tenant clinique = cliniques.parId(resolveur.tenantDeRendezVous(reference)
				.orElseThrow(RendezVousInconnu::new));
		return ContexteTenant.avec(clinique.id(), () -> {
			RendezVous rdv = dao.parReference(clinique.id(), reference)
					.orElseThrow(RendezVousInconnu::new);
			return action.apply(clinique, rdv);
		});
	}

	private VueRendezVous vue(Tenant clinique, RendezVous rdv) {
		return new VueRendezVous(clinique.nom(), rdv.reference(), rdv.jour(), rdv.heure(),
				rdv.statut(), rdv.motif());
	}

	public record VueCreneau(LocalDate jour, LocalTime heure, int placesRestantes) {
	}

	public record VueRendezVous(String clinique, String reference, LocalDate jour,
			LocalTime heure, String statut, String motif) {
	}
}
