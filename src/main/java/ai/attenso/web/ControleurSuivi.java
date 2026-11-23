package ai.attenso.web;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.Fourchette;
import ai.attenso.ticket.ResolveurReference;
import ai.attenso.ticket.ServiceEstimation;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import ai.attenso.ticket.TicketInconnu;
import ai.attenso.ticket.TicketRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * La page « où en est mon tour ».
 *
 * <p>La référence du lien tient lieu de justificatif. Elle ne donne accès qu'à
 * une position dans une file, elle est cloisonnée à une clinique, et elle est
 * courte — ce qu'un jeton signé n'est pas, et c'est la contrainte qui tranche :
 * le lien doit tenir dans un SMS d'un seul segment.
 *
 * <p><strong>Elle ne vaut que pour la journée de son ticket</strong>, et c'est
 * vérifié ici plutôt que supposé. La ligne {@code ticket} survit à la journée —
 * c'est elle qui porte le compteur de départs — donc rien n'expire tout seul :
 * sans ce contrôle, le lien reçu par SMS resterait ouvert indéfiniment.
 */
@RestController
public class ControleurSuivi {

	private static final int CONSULTATIONS_MAX = 60;

	private static final Duration FENETRE = Duration.ofMinutes(1);

	private final ResolveurReference resolveur;

	private final TicketRepository tickets;

	private final ServiceTenant cliniques;

	private final ServiceFile file;

	private final ServiceEstimation estimation;

	private final DiffusionFile diffusion;

	private final LimiteurDebit limiteur;

	public ControleurSuivi(ResolveurReference resolveur, TicketRepository tickets,
			ServiceTenant cliniques, ServiceFile file, ServiceEstimation estimation,
			DiffusionFile diffusion, LimiteurDebit limiteur) {
		this.resolveur = resolveur;
		this.tickets = tickets;
		this.cliniques = cliniques;
		this.file = file;
		this.estimation = estimation;
		this.diffusion = diffusion;
		this.limiteur = limiteur;
	}

	@GetMapping("/api/suivi/{reference}")
	public EtatSuivi etat(@PathVariable String reference) {
		if (!limiteur.autoriser("suivi:" + reference, CONSULTATIONS_MAX, FENETRE)) {
			throw new TropDeRequetes();
		}
		UUID tenantId = resolveur.tenantDe(reference)
				.orElseThrow(TicketInconnu::parReference);
		Tenant clinique = cliniques.parId(tenantId);
		return ContexteTenant.avec(tenantId, () -> {
			Ticket ticket = duJour(reference, clinique);
			int position = file.position(ticket);
			Fourchette attente = estimation.fourchette(tenantId, position);
			return new EtatSuivi(clinique.nom(), ticket.numeroJour(), position,
					ticket.statut().name(), attente.minMinutes(), attente.maxMinutes(),
					clinique.seuilPrevenir());
		});
	}

	@GetMapping(value = "/api/suivi/{reference}/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter flux(@PathVariable String reference) {
		UUID tenantId = resolveur.tenantDe(reference)
				.orElseThrow(TicketInconnu::parReference);
		Tenant clinique = cliniques.parId(tenantId);
		return ContexteTenant.avec(tenantId,
				() -> diffusion.abonner(tenantId, duJour(reference, clinique).jour()));
	}

	/**
	 * Le ticket de cette référence, à condition qu'il soit d'aujourd'hui dans
	 * le fuseau de la clinique. Un lien de la veille est traité exactement
	 * comme une référence inconnue : même code, même message, rien à déduire.
	 */
	private Ticket duJour(String reference, Tenant clinique) {
		return tickets.findByReference(reference)
				.filter(ticket -> ticket.jour().equals(file.jourCourant(clinique)))
				.orElseThrow(TicketInconnu::parReference);
	}
}
